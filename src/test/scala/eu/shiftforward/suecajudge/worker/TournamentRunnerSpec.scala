package eu.shiftforward.suecajudge.worker

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{ Actor, Props }
import net.ruippeixotog.akka.testkit.specs2.mutable.AkkaSpecification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.Scope

import eu.shiftforward.suecajudge.game.{ Card, Game }
import eu.shiftforward.suecajudge.safeexec.Cpp
import eu.shiftforward.suecajudge.safeexec.Runner.TimeLimitExceeded
import eu.shiftforward.suecajudge.storage.Submission
import eu.shiftforward.suecajudge.tournament.SwissTournament
import eu.shiftforward.suecajudge.worker.TournamentRunner.GameExecError

class TournamentRunnerSpec(implicit ee: ExecutionEnv) extends AkkaSpecification {

  def dummySubmission(user: String) = Submission(None, user, Cpp, "", Array.emptyByteArray)

  def validCard(st: Game): Card = {
    val nextPlayerHand = st.hands(st.nextPlayer.get)
    nextPlayerHand.find { c => st.trickSuit.contains(c.suit) }.getOrElse(nextPlayerHand.head)
  }

  def invalidCard(st: Game): Option[Card] = {
    st.hands(st.nextPlayer.get).partition { c => st.trickSuit.contains(c.suit) } match {
      case (Nil, _) => None
      case (_, nonSameSuit) => Some(nonSameSuit.head)
    }
  }

  class TestScope extends Scope {
    var failingUsers: Set[String] = Set.empty

    var submissionsRan = 0

    class PlayerActor extends Actor {
      override def receive = {
        case SubmissionRunnerActor.Run(sub: Submission, game) =>
          val res =
            if (!failingUsers(sub.user)) Right(validCard(game))
            else {
              failingUsers -= sub.user
              if (Random.nextBoolean() && invalidCard(game).isDefined) Right(invalidCard(game).get)
              else Left(TimeLimitExceeded)
            }

          submissionsRan += 1
          sender() ! SubmissionRunnerActor.Result(res, None)
      }
    }

    val playerMap = (1 to 10).map { i => s"p$i" -> dummySubmission(s"p$i") }.toMap
    val subRunnerActor = system.actorOf(Props(new PlayerActor))
  }

  // we can improve these tests if needed
  "A TournamentRunner" should {

    "create and run tournaments until their end" in new TestScope {
      new TournamentRunner(playerMap, subRunnerActor, 8, (_, _, _) => (), (_, _, _) => (), false).run() must
        beLike[SwissTournament] {
          case t =>
            t.isFinished must beTrue
            t.totalRounds ==== 8

            // there should be exactly 8 rounds, each one with 5 matches
            t.scores.map { sc => sc.wins + sc.draws + sc.losses }.sum ==== 2 * 8 * 5

            // each match has 5 games, each one needing 40 submissions
            submissionsRan ==== 8 * 5 * 5 * 40
        }.awaitFor(1.minute)
    }

    "make a player lose immediately a match if it provides an illegal answer" in new TestScope {
      failingUsers += "p2" // player 2 will fail its first submission run (but not subsequent ones)

      var failedUsers = List.empty[String]
      def onSubmissionInvalid(sub: Submission, game: Game, err: GameExecError) =
        failedUsers = sub.user :: failedUsers

      new TournamentRunner(playerMap, subRunnerActor, 8, onSubmissionInvalid, (_, _, _) => (), false).run() must
        beLike[SwissTournament] {
          case t =>
            t.isFinished must beTrue
            t.totalRounds ==== 8

            // there should be exactly 8 rounds, each one with 5 matches (one of the matches erroring)
            t.scores.map { sc => sc.wins + sc.draws + sc.losses }.sum ==== 2 * 8 * 5

            // player 2 must finish with 0 points
            failedUsers ==== List("p2")
            t.scores.find(_.player == "p2") must beSome.which(_.score == 0.0)

            // all the matches with player 2 combined have at most two submissions;
            // other matches have 5 games, each one needing 40 submissions
            submissionsRan must beBetween(8 * 4 * 5 * 40 + 1, 8 * 4 * 5 * 40 + 2)
        }.awaitFor(1.minute)
    }
  }
}
