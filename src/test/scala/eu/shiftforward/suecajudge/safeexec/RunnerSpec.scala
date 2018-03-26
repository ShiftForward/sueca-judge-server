package eu.shiftforward.suecajudge.safeexec

import scala.concurrent.duration._
import scala.io.Source

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import eu.shiftforward.suecajudge.game.{ Card, Game }
import eu.shiftforward.suecajudge.safeexec.Runner._
import eu.shiftforward.suecajudge.safeexec.Stats.Code
import eu.shiftforward.suecajudge.storage.Submission

class RunnerSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll {

  val runner = new Runner
  override def afterAll(): Unit = runner.destroy()

  def submissionFor(file: String, lang: Language): Submission = {
    Submission(None, "", lang, "", Source.fromResource(s"safeexec/$file").mkString.getBytes)
  }

  "a Runner" should {

    "correctly run a valid submission" in {
      val submission = submissionFor("player_valid.cpp", Cpp)
      val game = Game.newGame()
      runner.run(submission, game).map(_.res) must beRight(game.hands(game.nextPlayer.get).head).awaitFor(10.seconds)
    }

    "identify correctly time limit exceeded" in {
      val submission = submissionFor("infinite_loop.c", C)
      val game = Game.newGame()
      runner.run(submission, game).map(_.res) must beLeft(TimeLimitExceeded: ErrorCode).awaitFor(10.seconds)
    }

    "identify correctly memory limit exceeded" in {
      val submission = submissionFor("infinite_alloc.cpp", Cpp)
      val game = Game.newGame()
      runner.run(submission, game).map(_.res) must beLeft(MemoryLimitExceeded: ErrorCode).awaitFor(10.seconds)
    }

    "identify correctly runtime errors" in {
      val submission = submissionFor("exit_1.c", C)
      val game = Game.newGame()
      runner.run(submission, game).map(_.res) must beLeft(RuntimeError: ErrorCode).awaitFor(10.seconds)
    }

    "identify correctly compile-time errors" in {
      def testGarbageSubmission(language: Language, out: String = "", err: String = "") = {
        val submission = submissionFor("garbage", language)
        val game = Game.newGame()
        runner.run(submission, game).map(_.res) must beLike[Either[ErrorCode, Card]] {
          case Left(CompilationError(`out`, `err`, Code.RuntimeError(1))) => ok
          case x => ko(x.toString)
        }.awaitFor(5.seconds)
      }
      testGarbageSubmission(C, err = """main.c:1:1: error: unknown type name 'This'
                                        | This is a garbage file
                                        | ^~~~
                                        |main.c:1:9: error: expected '=', ',', ';', 'asm' or '__attribute__' before 'a'
                                        | This is a garbage file
                                        |         ^
                                        |main.c:1:9: error: unknown type name 'a'""".stripMargin)
      testGarbageSubmission(Cpp, err = """main.cpp:1:1: error: 'This' does not name a type
                                         | This is a garbage file
                                         | ^~~~""".stripMargin)
      testGarbageSubmission(Java, err = """Main.java:1: error: class, interface, or enum expected
                                          |This is a garbage file
                                          |^
                                          |1 error""".stripMargin)
      testGarbageSubmission(Python, out = """Compiling 'main.py'...
                                            |***   File "main.py", line 1
                                            |    This is a garbage file
                                            |                    ^
                                            |SyntaxError: invalid syntax""".stripMargin)
    }
  }
}
