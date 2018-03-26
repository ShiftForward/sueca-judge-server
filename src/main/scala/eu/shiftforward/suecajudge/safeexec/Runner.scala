package eu.shiftforward.suecajudge.safeexec

import scala.concurrent.{ ExecutionContext, Future }

import io.circe._
import io.circe.generic.semiauto._
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.game.{ Card, Game, PartialGame }
import eu.shiftforward.suecajudge.safeexec.Runner._
import eu.shiftforward.suecajudge.safeexec.SafeExec._
import eu.shiftforward.suecajudge.safeexec.Stats.Code
import eu.shiftforward.suecajudge.safeexec.Stats.Code.OK
import eu.shiftforward.suecajudge.safeexec.codec.Decoder._
import eu.shiftforward.suecajudge.safeexec.codec.Encoder._
import eu.shiftforward.suecajudge.storage.Submission

class Runner extends Logging {
  private[this] val safeExec = new SafeExec

  def run(submission: Submission, input: Game)(implicit ec: ExecutionContext): Future[RunResult] = {
    require(input.nextPlayer.nonEmpty)
    run(submission, PartialGame(input, input.nextPlayer.get).encode)
  }

  def run(submission: Submission, input: String)(implicit ec: ExecutionContext): Future[RunResult] = {

    def mapSafeExecResult(safeExecRes: SafeExec.ExecResult): Either[ErrorCode, Card] = {
      val runtimeStatus = safeExecRes match {
        case res if res.dockerExitCode != 0 =>
          logger.error(s"Docker exited with a non-zero status code ($res).")
          Left(Fatal)
        case ExecResult(_, runtime) =>
          Right(runtime)
      }

      runtimeStatus.flatMap {
        case Execution(stdout, _, Stats(Code.OK, _, _, _)) => stdout.decode[Card] match {
          case Some(card) => Right(card)
          case None => Left(MalformedOutput(stdout))
        }
        case Execution(_, _, Stats(Code.RuntimeError(_) | Code.SignalError(_), _, _, _)) =>
          Left(RuntimeError)
        case Execution(_, _, Stats(Code.TimeLimitExceeded, _, _, _)) =>
          Left(TimeLimitExceeded)
        case Execution(_, _, Stats(Code.MemoryLimitExceeded, _, _, _)) =>
          Left(MemoryLimitExceeded)
        case Execution(_, _, Stats(Code.InternalError, _, _, _)) =>
          logger.error(s"Safeexec internal error.")
          Left(Unknown)
        case Execution(_, _, Stats(Code.Unknown(x), _, _, _)) =>
          logger.error(s"Unknown safeexec error ($x).")
          Left(Unknown)
      }
    }

    submission.compilationResult.map { compBytes =>
      safeExec.run(submission.language, compBytes, input).map(r => RunResult(mapSafeExecResult(r), Some(compBytes)))
    }.getOrElse {
      safeExec.compile(submission.language, submission.payload).flatMap {
        case CompilationResult(dockerExitCode, _, compBytesOpt) if dockerExitCode != 0 =>
          logger.error(s"Docker exited with a non-zero status code.")
          Future.successful(RunResult(Left(Fatal), compBytesOpt))
        case CompilationResult(_, Execution(_, _, Stats(OK, _, _, _)), Some(compBytes)) =>
          safeExec.run(submission.language, compBytes, input).map(r => RunResult(mapSafeExecResult(r), Some(compBytes)))
        case CompilationResult(_, Execution(_, _, Stats(OK, _, _, _)), None) =>
          logger.error(s"Compilation phase returned no compiled output!")
          Future.successful(RunResult(Left(Fatal), None))
        case CompilationResult(_, Execution(stdout, stderr, Stats(code, _, _, _)), compBytesOutput) =>
          Future.successful(RunResult(Left(CompilationError(stdout, stderr, code)), compBytesOutput))
      }
    }
  }

  def destroy() = safeExec.destroy()
}

object Runner {

  sealed trait ErrorCode
  case object MemoryLimitExceeded extends ErrorCode
  case object TimeLimitExceeded extends ErrorCode
  case object RuntimeError extends ErrorCode
  case class CompilationError(stdout: String, stderr: String, error: Code) extends ErrorCode
  case class MalformedOutput(stdout: String) extends ErrorCode
  case object WrongOutput extends ErrorCode
  case object Unknown extends ErrorCode
  case object Fatal extends ErrorCode

  case class RunResult(res: Either[ErrorCode, Card], compiledBytes: Option[Array[Byte]])

  implicit val errorCodeEncoder: Encoder[ErrorCode] = deriveEncoder[ErrorCode]
  implicit val errorCodeDecoder: Decoder[ErrorCode] = deriveDecoder[ErrorCode]
}
