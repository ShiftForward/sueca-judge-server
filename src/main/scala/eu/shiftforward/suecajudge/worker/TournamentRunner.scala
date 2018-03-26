package eu.shiftforward.suecajudge.worker

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.game.{ Card, Game, Match, getTeam }
import eu.shiftforward.suecajudge.safeexec.Runner
import eu.shiftforward.suecajudge.safeexec.Runner.WrongOutput
import eu.shiftforward.suecajudge.storage.Submission
import eu.shiftforward.suecajudge.tournament._
import eu.shiftforward.suecajudge.worker.EitherUtils._
import eu.shiftforward.suecajudge.worker.TournamentRunner._

class TournamentRunner(
    playerMap: Map[String, Submission],
    submissionRunner: ActorRef,
    targetRounds: Int,
    onSubmissionInvalid: (Submission, Game, GameExecError) => Unit,
    onMatchFinished: (Submission, Submission, Match) => Unit,
    autoPlayOptimization: Boolean)(implicit ec: ExecutionContext) extends Logging {

  require(playerMap.nonEmpty)

  implicit private val timeout = Timeout(30.seconds)

  def run(): Future[SwissTournament] = {
    runTournament {
      if (playerMap.size <= targetRounds) {
        val fullRoundRobinRounds = if (playerMap.size % 2 == 0) playerMap.size - 1 else playerMap.size
        val nRounds = (targetRounds / fullRoundRobinRounds) * fullRoundRobinRounds
        logger.info(s"Starting a new round-robin tournament with $nRounds rounds")
        SwissTournament(playerMap.keySet, nRounds, CircleMethodPairing)
      } else {
        val nRounds = math.max(targetRounds, math.ceil(math.log(playerMap.size) / math.log(2)).toInt)
        logger.info(s"Starting a new swiss tournament with $nRounds rounds")
        SwissTournament(playerMap.keySet, nRounds, MaximumWeightMatchingPairing)
      }
    }
  }

  def runTournament(tournament: SwissTournament): Future[SwissTournament] = {
    if (tournament.isFinished) Future.successful(tournament)
    else {
      val (round, t) = tournament.drawNextRound.getOrThrow
      if (round.isFinished) runTournament(t)
      else {
        runRound(round).map { r =>
          logger.info(s"Finished round ${t.currentRoundNumber}/${t.totalRounds}")
          t.offerRound(r).getOrThrow
        }.flatMap(runTournament)
      }
    }
  }

  def runRound(round: Round): Future[Round] = {
    def toPairingResult(p: Pairing, res: Either[GameExecError, Match]): Result = res match {
      case Right(m) if m.winner.contains(0) => p.winPlayer1
      case Right(m) if m.winner.contains(1) => p.winPlayer2
      case Right(_) => p.draw
      case Left(err) if err.player == 0 => p.errorPlayer1
      case Left(_) => p.errorPlayer2
    }
    val matchesFut = Future.sequence(round.pairings.filter(_._2.isEmpty).keys.map { p =>
      runMatch(playerMap(p.player1), playerMap(p.player2), Match.newMatch()).map { res =>
        val pRes = toPairingResult(p, res)
        res.right.foreach(onMatchFinished(playerMap(p.player1), playerMap(p.player2), _))
        logger.info(s"Finished match between ${p.player1} and ${p.player2} ($pRes)")
        p -> pRes
      }
    })
    matchesFut.map(_.foldLeft(round) { case (r, (p, res)) => r.offerResult(p, res).getOrThrow })
  }

  def runMatch(sub1: Submission, sub2: Submission, m: Match): Future[Either[GameExecError, Match]] = {
    if (m.isFinished) Future.successful(Right(m))
    else {
      val game = m.drawNextGame().getOrThrow
      runGame(sub1, sub2, game)
        .map { _.map(m.offerGame(_).getOrThrow) }
        .flatMap(_.map(runMatch(sub1, sub2, _)).flatSequence[Match])
    }
  }

  def runGame(sub1: Submission, sub2: Submission, game: Game): Future[Either[GameExecError, Game]] = {
    if (game.isFinished) Future.successful(Right(game))
    else {
      val player = getTeam(game.nextPlayer.get)
      val sub = if (player == 0) sub1 else sub2

      def toGameResult(res: Either[Runner.ErrorCode, Card]): Either[GameExecError, Game] = res.map(game.play) match {
        case Right(Right(card)) => Right(card)
        case Right(Left(_)) => Left(GameExecError(player, WrongOutput))
        case Left(code) => Left(GameExecError(player, code))
      }

      def runSubmission(nRetries: Int): Future[Either[Runner.ErrorCode, Card]] =
        (submissionRunner ? SubmissionRunnerActor.Run(sub, game)).mapTo[SubmissionRunnerActor.Result].map(_.res).transformWith {
          case Failure(t) =>
            if (nRetries == 0) Future.failed(t) else runSubmission(nRetries - 1)
          case Success(res @ Left(Runner.Unknown)) =>
            if (nRetries == 0) Future.successful(res) else runSubmission(nRetries - 1)
          case Success(res) => Future.successful(res)
        }

      val res =
        if (autoPlayOptimization) {
          game.validCards match {
            case c :: Nil => Future.successful(Right(c))
            case _ => runSubmission(3)
          }
        } else runSubmission(3)

      res
        .map(toGameResult(_).left.map { err => onSubmissionInvalid(sub, game, err); err })
        .flatMap(_.map(runGame(sub1, sub2, _)).flatSequence[Game])
    }
  }

  def defaultRounds: Int =
    math.ceil(math.log(playerMap.size) / math.log(2)).toInt
}

object TournamentRunner {
  case class GameExecError(player: Int, code: Runner.ErrorCode)
}
