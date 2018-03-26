package eu.shiftforward.suecajudge.worker

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.game._
import eu.shiftforward.suecajudge.safeexec.Runner.{ ErrorCode, Fatal, WrongOutput }
import eu.shiftforward.suecajudge.safeexec.codec.Decoder._
import eu.shiftforward.suecajudge.storage.Submission
import eu.shiftforward.suecajudge.worker.SubmissionRunnerActor.Result
import eu.shiftforward.suecajudge.worker.Validator._

class Validator(submissionRunner: ActorRef)(implicit ec: ExecutionContext) extends Logging {
  implicit private val timeout: Timeout = Timeout(30.seconds)

  def validate(submission: Submission): Future[ValidationResult] = {
    TestCases.foldLeft(Future.successful(ValidationResult(Option.empty[ErrorCode], None))) {
      case (acc, input) => acc.flatMap {
        case ValidationResult(None, _) => runAndValidate(submission, input)
        case ValidationResult(Some(res), compBytes) => Future.successful(ValidationResult(Some(res), compBytes))
      }
    }
  }

  private def runAndValidate(submission: Submission, input: String): Future[ValidationResult] = {
    (submissionRunner ? SubmissionRunnerActor.RunInput(submission, input)).mapTo[SubmissionRunnerActor.Result].map {
      case Result(Left(err), compBytes) =>
        ValidationResult(Some(err), compBytes)
      case Result(res, None) =>
        logger.error(s"Unexpected result from submission actor, with no compiled output: '$res'")
        ValidationResult(Some(Fatal), None)
      case Result(Right(c), compBytes) =>
        if (!isCardValid(input.decode[PartialGame].get, c)) ValidationResult(Some(WrongOutput), compBytes)
        else ValidationResult(None, compBytes)
    }
  }

  private def isCardValid(game: PartialGame, card: Card): Boolean =
    game.hand.filter(game.trickSuit.contains) match {
      case Nil => game.hand.contains(card)
      case ls => ls.contains(card)
    }
}

object Validator {
  case class ValidationResult(error: Option[ErrorCode], compiledBytes: Option[Array[Byte]])

  val TestCases = List(
    Source.fromResource("testcases/first_trick_first_hand.txt").mkString,
    Source.fromResource("testcases/first_trick_user_doesnt_have_suit.txt").mkString,
    Source.fromResource("testcases/first_trick_user_has_suit.txt").mkString,
    Source.fromResource("testcases/7th_trick_user_doesnt_have_suit.txt").mkString,
    Source.fromResource("testcases/7th_trick_user_has_suit.txt").mkString) ++
    (1 to 25).map { i => Source.fromResource(f"testcases/additional_$i%02d.txt").mkString }
}
