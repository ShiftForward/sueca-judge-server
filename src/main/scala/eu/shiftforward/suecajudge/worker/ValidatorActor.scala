package eu.shiftforward.suecajudge.worker

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Status }
import akka.pattern.pipe
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.storage.{ DB, Submission }
import eu.shiftforward.suecajudge.worker.Validator.ValidationResult
import eu.shiftforward.suecajudge.worker.ValidatorActor.{ Finished, Run, Submissions }

class ValidatorActor(submissionRunnerPool: ActorRef, validatorConf: Config) extends Actor with Logging {
  implicit private def ec = context.dispatcher

  private val initialDelay = FiniteDuration(validatorConf.getDuration("initial-delay").toMillis, MILLISECONDS)
  private val runInterval = FiniteDuration(validatorConf.getDuration("run-interval").toMillis, MILLISECONDS)

  val validator = new Validator(submissionRunnerPool)

  override def preStart(): Unit = {
    context.system.scheduler.schedule(initialDelay, runInterval, self, Run)
  }

  def becomeIdle(hasQueuedRuns: Boolean): Unit = {
    if (hasQueuedRuns) self ! Run
    context.become(idle)
  }

  def idle: Receive = {
    case Run =>
      logger.info("Fetching submissions pending validation...")
      DB.players.latestPendingSubmissions().map { subs =>
        self ! Submissions(subs)
      }
      context.become(waitingForSubmissions(false))
  }

  def waitingForSubmissions(hasQueuedRuns: Boolean): Receive = {
    case Run if !hasQueuedRuns =>
      context.become(waitingForSubmissions(true))

    case Submissions(subs) if subs.isEmpty =>
      logger.info(s"No submissions pending validation")
      becomeIdle(hasQueuedRuns)

    case Submissions(subs) =>
      logger.info(s"${subs.length} submissions pending validation")

      val futs = subs.map { s =>
        validator.validate(s).map {
          case ValidationResult(None, compBytes) => (Submission.PreTested, compBytes)
          case ValidationResult(Some(err), compBytes) => (Submission.Error(err), compBytes)
        }.flatMap {
          case (state, compBytes) =>
            DB.players.setSubmissionState(s.id.get, state).flatMap { _ =>
              compBytes.map(DB.players.setSubmissionCompilationResult(s.id.get, _)).getOrElse(Future.successful())
            }
        }
      }
      Future.sequence(futs).map(_ => Finished).pipeTo(self)
      context.become(processing(hasQueuedRuns))

    case Status.Failure(cause) =>
      logger.error("An error occurred when fetching submissions", cause)
      becomeIdle(hasQueuedRuns)
  }

  def processing(hasQueuedRuns: Boolean): Receive = {
    case Run if !hasQueuedRuns =>
      context.become(processing(true))

    case Finished =>
      logger.info(s"Validations finished")
      becomeIdle(hasQueuedRuns)

    case Status.Failure(cause) =>
      logger.error("An error occurred when validating submissions", cause)
      becomeIdle(hasQueuedRuns)
  }

  def receive = idle
}

object ValidatorActor {
  case object Run
  case class Submissions(subs: Seq[Submission])
  case object Finished
}
