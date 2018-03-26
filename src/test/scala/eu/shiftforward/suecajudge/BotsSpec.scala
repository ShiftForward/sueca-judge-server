package eu.shiftforward.suecajudge

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

import akka.actor.Props
import akka.routing.RoundRobinPool
import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import org.specs2.concurrent.ExecutionEnv

import eu.shiftforward.suecajudge.game.Game
import eu.shiftforward.suecajudge.safeexec._
import eu.shiftforward.suecajudge.storage.Submission
import eu.shiftforward.suecajudge.storage.Submission.PreTested
import eu.shiftforward.suecajudge.worker.Validator.ValidationResult
import eu.shiftforward.suecajudge.worker.{ SubmissionRunnerActor, TournamentRunner, Validator }

class BotsSpec(implicit ee: ExecutionEnv) extends AkkaSpecification {

  def submissionFor(file: String, lang: Language): Submission =
    Submission(None, "", lang, "random", Source.fromFile(s"bots/$file").mkString.getBytes, None, PreTested, 0)

  val submissions = List(
    submissionFor("random.cpp", Cpp),
    submissionFor("Main.java", Java),
    submissionFor("random.c", C),
    submissionFor("random.py", Python),
    submissionFor("random.js", JavaScript))

  lazy val submissionRunnerPool = system.actorOf(RoundRobinPool(2).props(Props(new SubmissionRunnerActor)))
  lazy val validator = new Validator(submissionRunnerPool)
  lazy val runner =
    new TournamentRunner(
      Map("dummy" -> null),
      submissionRunnerPool,
      8,
      (_, _, _) => (),
      (_, _, _) => (),
      true)

  submissions.foreach { sub =>
    s"The ${sub.language.name} example bot" should {

      "pass the pretests" in {
        validator.validate(sub).map(_.error) must beNone.awaitFor(10.minutes)
      }

      "run a game correctly until the end" in {
        validator.validate(sub).flatMap {
          case ValidationResult(None, compBytes) =>
            val subWithCompBytes = sub.copy(compilationResult = compBytes)
            runner.runGame(subWithCompBytes, subWithCompBytes, Game.newGame())
          case ValidationResult(Some(_), _) => Future.failed(new RuntimeException("Validation Failed!"))
        } must beRight.awaitFor(10.minutes)
      }
    }
  }
}
