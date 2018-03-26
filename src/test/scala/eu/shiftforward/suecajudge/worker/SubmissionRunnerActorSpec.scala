package eu.shiftforward.suecajudge.worker

import scala.concurrent.duration._
import scala.io.Source

import akka.actor.Props
import akka.testkit.TestProbe
import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification

import eu.shiftforward.suecajudge.game.Game
import eu.shiftforward.suecajudge.safeexec.Runner.TimeLimitExceeded
import eu.shiftforward.suecajudge.safeexec.{ C, Cpp, Language }
import eu.shiftforward.suecajudge.storage.Submission

class SubmissionRunnerActorSpec extends AkkaSpecification {

  def submissionFor(file: String, lang: Language): Submission = {
    val source = Source.fromResource(s"safeexec/$file").mkString.getBytes
    Submission(None, "", lang, "", source)
  }

  "A SubmissionRunnerActor" should {

    "run correctly a valid submission" in {
      val submission = submissionFor("player_valid.cpp", Cpp)
      val game = Game.newGame()

      val actor = system.actorOf(Props(new SubmissionRunnerActor))
      val probe = TestProbe()
      probe.send(actor, SubmissionRunnerActor.Run(submission, game))

      probe must receiveWithin(10.seconds).like {
        case SubmissionRunnerActor.Result(Right(_), _) => ok
      }
    }

    "identify errors correctly" in {
      val submission = submissionFor("infinite_loop.c", C)
      val game = Game.newGame()

      val actor = system.actorOf(Props(new SubmissionRunnerActor))
      val probe = TestProbe()
      probe.send(actor, SubmissionRunnerActor.Run(submission, game))

      probe must receiveWithin(10.seconds).like {
        case SubmissionRunnerActor.Result(Left(TimeLimitExceeded), _) => ok
      }
    }
  }
}
