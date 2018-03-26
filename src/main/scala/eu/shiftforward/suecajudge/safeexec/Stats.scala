package eu.shiftforward.suecajudge.safeexec

import scala.concurrent.duration._
import scala.util.matching.Regex

import io.circe._
import io.circe.generic.semiauto._
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.safeexec.Stats.Code

case class Stats(code: Code, elapsedTime: Duration, memoryUsage: Long, cpuUsage: Duration)

object Stats extends Logging {

  sealed trait Code

  object Code {
    case object OK extends Code
    case object TimeLimitExceeded extends Code
    case object MemoryLimitExceeded extends Code
    case object InternalError extends Code
    case class SignalError(signal: String) extends Code
    case class RuntimeError(exitCode: Int) extends Code
    case class Unknown(message: String) extends Code

    implicit val codeEncoder: Encoder[Code] = deriveEncoder[Code]
    implicit val codeDecoder: Decoder[Code] = deriveDecoder[Code]

    private[this] val runtimeErrorRegex = """Command exited with non-zero status \((\d+)\)""".r
    private[this] val signalRegex = """Command terminated by signal \((.+)\)""".r

    def fromString(message: String): Code = message match {
      case "OK" => OK
      case "Time Limit Exceeded" => TimeLimitExceeded
      case "Memory Limit Exceeded" => MemoryLimitExceeded
      case "Internal Error" => InternalError
      case signalRegex(signal) => SignalError(signal)
      case runtimeErrorRegex(exitCodeStr) => RuntimeError(exitCodeStr.toInt)
      case _ =>
        logger.error(s"Unknown status message: '$message'")
        Unknown(message)
    }
  }

  private[this] val ElapsedTime: Regex = "elapsed time: ([0-9]*) seconds".r
  private[this] val MemoryUsage: Regex = "memory usage: ([0-9]*) kbytes".r
  private[this] val CpuUsage: Regex = "cpu usage: ([0-9\\.]*) seconds".r

  def apply(statString: String): Stats = {
    val splitOutput = statString.split("\n").toList
    splitOutput match {
      case message :: ElapsedTime(time) :: MemoryUsage(memory) :: CpuUsage(cpu) :: Nil =>
        Stats(Code.fromString(message), time.toInt.seconds, memory.toLong, cpu.toDouble.seconds)
      case _ =>
        logger.warn(s"Failed to parse stats: '$statString'")
        Stats(Code.Unknown("Failed to parse stats"), Duration.Undefined, 0, Duration.Undefined)
    }
  }
}
