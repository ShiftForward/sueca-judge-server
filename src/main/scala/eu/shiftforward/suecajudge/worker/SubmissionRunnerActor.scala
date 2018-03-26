package eu.shiftforward.suecajudge.worker

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Stash, Status }
import akka.pattern.pipe
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.game.{ Card, Game }
import eu.shiftforward.suecajudge.safeexec.Runner
import eu.shiftforward.suecajudge.safeexec.Runner.{ Fatal, Unknown }
import eu.shiftforward.suecajudge.storage.Submission
import eu.shiftforward.suecajudge.worker.SubmissionRunnerActor._

class SubmissionRunnerActor extends Actor with Stash with Logging {
  implicit val ec = context.dispatcher

  var submissionsRan = 0

  override def preStart(): Unit = {
    context.system.scheduler.schedule(5.minutes, 5.minutes, self, PrintStats)
  }

  var runner = new Runner()
  def run(cmd: RunCmd): Future[Result] = cmd match {
    case Run(sub, game) => runner.run(sub, game).map(r => Result(r.res, r.compiledBytes))
    case RunInput(sub, input) => runner.run(sub, input).map(r => Result(r.res, r.compiledBytes))
  }
  logger.info(s"Submission worker started on $self")

  def becomeIdle(): Unit = {
    unstashAll()
    context.become(idle)
  }

  def idle: Receive = {
    case cmd: RunCmd =>
      run(cmd).pipeTo(self)
      context.become(processing(sender()))
    case PrintStats =>
      logger.info(s"$submissionsRan successful submissions ran until now")
  }

  def processing(replyTo: ActorRef): Receive = {
    case _: Run => stash()
    case _: RunInput => stash()
    case res @ Result(Left(Fatal), _) =>
      logger.error(s"A fatal error occurred, restarting Runner on $self")
      runner.destroy()
      runner = new Runner()
      replyTo ! res.copy(res = Left(Unknown))
    case res @ Result(Left(Unknown), _) =>
      replyTo ! res
    case res: Result =>
      submissionsRan += 1
      replyTo ! res
      becomeIdle()
    case err: Status.Failure =>
      logger.error("An error occurred running a submission", err.cause)
      replyTo ! err
      becomeIdle()
    case PrintStats =>
      logger.info(s"$submissionsRan successful submissions ran until now")
  }

  def receive = idle

  override def postStop(): Unit = {
    super.postStop()
    runner.destroy()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    runner.destroy()
  }
}

object SubmissionRunnerActor {
  sealed trait RunCmd
  case class Run(sub: Submission, game: Game) extends RunCmd
  case class RunInput(sub: Submission, input: String) extends RunCmd
  case class Result(res: Either[Runner.ErrorCode, Card], compBytes: Option[Array[Byte]])
  case object PrintStats
}
