package eu.shiftforward.suecajudge.worker

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Status }
import akka.pattern.pipe
import com.typesafe.config.Config
import org.apache.logging.log4j.{ LogManager, Logger }
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.game.{ Game, Match, PartialGame }
import eu.shiftforward.suecajudge.safeexec.Runner.{ RuntimeError, WrongOutput }
import eu.shiftforward.suecajudge.safeexec.codec.Encoder._
import eu.shiftforward.suecajudge.storage.{ DB, Submission }
import eu.shiftforward.suecajudge.tournament.{ Result, SwissTournament }
import eu.shiftforward.suecajudge.worker.TournamentRunner.GameExecError
import eu.shiftforward.suecajudge.worker.TournamentRunnerActor.{ Players, Run }

class TournamentRunnerActor(submissionRunnerPool: ActorRef, tournamentConf: Config) extends Actor with Logging {
  implicit private def ec = context.dispatcher

  private val autoPlay = tournamentConf.getBoolean("auto-play")
  private val initialDelay = FiniteDuration(tournamentConf.getDuration("initial-delay").toMillis, MILLISECONDS)
  private val runInterval = FiniteDuration(tournamentConf.getDuration("run-interval").toMillis, MILLISECONDS)
  private val targetRounds = tournamentConf.getInt("target-rounds")

  private val testcaseLogger = LogManager.getLogger("testcases")

  override def preStart(): Unit = {
    context.system.scheduler.schedule(initialDelay, runInterval, self, Run)
  }

  def onSubmissionInvalid(sub: Submission, game: Game, err: GameExecError): Unit = {
    logger.info(s"Submission from ${sub.user} invalidated: $err")
    DB.players.setSubmissionState(sub.id.get, Submission.Error(err.code)).failed.foreach { err =>
      logger.error(s"Failed changing the submission state to Error", err)
    }
    err.code match {
      case RuntimeError | WrongOutput =>
        testcaseLogger.warn(s"Input caused a ${err.code}:\n${PartialGame(game, err.player).encode}")
      case _ => // nothing to do here
    }
  }

  def onTournamentStarted(): Future[Long] =
    DB.players.startTournament().map(_.id.get)

  def onMatchFinished(tournamentId: Long, sub1: Submission, sub2: Submission, data: Match): Unit = {
    DB.players.addMatchEntry(tournamentId, sub1, sub2, data)
  }

  def onTournamentFinished(tournamentId: Long, t: SwissTournament, p: Map[String, Submission]): Unit = {
    val finishTime = System.currentTimeMillis()
    val scoresToUpdate = t.scores.filterNot(s => t.invalidatedPlayers.contains(s.player))

    DB.players.finishTournament(tournamentId, finishTime, t).failed.foreach { err =>
      logger.error(s"Failed finishing tournament with id $tournamentId.", err)
    }

    DB.players.updateLeaderboard(scoresToUpdate, tournamentId).failed.foreach { err =>
      logger.error(s"Failed adding tournament results to the database", err)
    }

    val idsToUpdate = p.filterKeys(!t.invalidatedPlayers.contains(_)).values.map(_.id.get)
    DB.players.setSubmissionsState(idsToUpdate, Submission.EvaluatedInTournament).failed.foreach { err =>
      logger.error(s"Failed changing the submission state to EvaluatedInTournament", err)
    }
  }

  def becomeIdle(hasQueuedRuns: Boolean): Unit = {
    if (hasQueuedRuns) self ! Run
    context.become(idle)
  }

  def idle: Receive = {
    case Run =>
      logger.info("Fetching submissions for running a new tournament...")
      DB.players.latestSubmissions().map { subs =>
        self ! Players(subs.map { s => s.user -> s }.toMap)
      }
      context.become(waitingForSubmissions(false))
  }

  def waitingForSubmissions(hasQueuedRuns: Boolean): Receive = {
    case Run if !hasQueuedRuns =>
      context.become(waitingForSubmissions(true))

    case Players(playerMap) if playerMap.size <= 1 =>
      logger.info(s"Not enough players eligible for participation, skipping tournament")
      becomeIdle(hasQueuedRuns)

    case Players(playerMap) =>
      logger.info(s"${playerMap.size} players eligible for participation")
      onTournamentStarted().flatMap { id =>
        new TournamentRunner(playerMap, submissionRunnerPool, targetRounds, onSubmissionInvalid, onMatchFinished(id, _, _, _), autoPlay)
          .run().map(id -> _)
      }.pipeTo(self)
      context.become(processing(hasQueuedRuns, playerMap))

    case Status.Failure(cause) =>
      logger.error("An error occurred when fetching submissions", cause)
      becomeIdle(hasQueuedRuns)
  }

  def processing(hasQueuedRuns: Boolean, playerMap: Map[String, Submission]): Receive = {
    case Run if !hasQueuedRuns =>
      context.become(processing(true, playerMap))

    case (id: Long, t: SwissTournament) =>
      logger.info(s"Tournament finished")
      onTournamentFinished(id, t, playerMap)
      becomeIdle(hasQueuedRuns)

    case Status.Failure(cause) =>
      logger.error("An error occurred when running the tournament", cause)
      becomeIdle(hasQueuedRuns)
  }

  def receive = idle
}

object TournamentRunnerActor {
  case object Run
  case class Players(playerMap: Map[String, Submission])
}
