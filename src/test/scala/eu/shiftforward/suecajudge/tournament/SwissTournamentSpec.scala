package eu.shiftforward.suecajudge.tournament

import java.util.UUID

import org.specs2.mutable.Specification

import eu.shiftforward.suecajudge.tournament.Round._
import eu.shiftforward.suecajudge.tournament.SwissTournament._

class SwissTournamentSpec extends Specification {
  def newPlayer() = UUID.randomUUID().toString
  def newTournament(nPlayers: Int, nRounds: Int, pairing: PairingStrategy = MaximumWeightMatchingPairing): SwissTournament =
    SwissTournament((0 until nPlayers).map(_ => newPlayer()).toSet, nRounds, pairing)
  def newRound(nPlayers: Int): Round =
    newTournament(nPlayers, 1).drawNextRound.right.get._1
  def finishRound(round: Round, result: (Pairing => Result) = _.winPlayer1): Round = {
    val pairings = round.pairings.keySet
    pairings.foldLeft[Either[Round.ValidationError, Round]](Right(round)) {
      case (roundE, p) =>
        roundE.flatMap(_.offerResult(p, result(p)))
    }.right.get
  }
  def runTournament(tournament: SwissTournament, result: (Pairing => Result) = _.winPlayer1): (SwissTournament, List[Round]) = {
    (0 until tournament.totalRounds).foldLeft((tournament, List.empty[Round])) {
      case ((t, rs), _) =>
        val (nextRound, nextT) = t.drawNextRound.right.get
        val finishedNextRound = finishRound(nextRound, result)
        (nextT.offerRound(finishedNextRound).right.get, rs :+ finishedNextRound)
    }
  }

  "A SwissTournament" should {
    "have an empty first round after being created" in {
      val tournament = newTournament(4, 2)
      tournament.currentRound must beNone
    }

    "have a status of not started after being created" in {
      val tournament = newTournament(4, 2)
      tournament.isStarted must beFalse
    }

    "have all players with a score of 0 after being created" in {
      val tournament = newTournament(4, 2)
      tournament.scores must containTheSameElementsAs(tournament.players.map(PlayerScore.empty).toList)
    }

    "properly create a first round" in {
      val tournament = newTournament(4, 2)
      tournament.drawNextRound must beRight.like {
        case (r, _) =>
          r.bye must beNone
          r.pairings.keySet.flatMap(_.players) mustEqual tournament.players
      }
    }

    "properly accept results in rounds" in {
      val round = newRound(4)
      val p = round.pairings.keySet.head
      round.offerResult(p, p.winPlayer1) must beRight
      round.offerResult(p, p.winPlayer2) must beRight
      round.offerResult(p, p.draw) must beRight
    }

    "reject results already recorded in rounds" in {
      val round = newRound(4)
      val pairing = round.pairings.keySet.head
      val nextR = round.offerResult(pairing, pairing.winPlayer1)
      nextR must beRight
      nextR.flatMap(_.offerResult(pairing, pairing.winPlayer1)) must beLeft(ResultAlreadyRecorded)
    }

    "reject unknown pairings in rounds" in {
      val round = newRound(4)
      val pairing1 :: pairing2 :: _ = round.pairings.keySet.toList
      val pairing = Pairing(pairing1.player1, pairing2.player2)
      round.offerResult(pairing, pairing.winPlayer2) must beLeft(UnexpectedPairing)
    }

    "have a round considered finished when all results are recorded" in {
      val round = newRound(4)
      round.isFinished must beFalse
      val pairing1 :: pairing2 :: _ = round.pairings.keySet.toList
      val r1 = round.offerResult(pairing1, pairing1.winPlayer2)
      r1 must beRight.like { case r => r.isFinished must beFalse }
      val r2 = r1.flatMap(_.offerResult(pairing2, pairing2.winPlayer1))
      r2 must beRight.like { case r => r.isFinished must beTrue }
    }

    "accept a finished round to update scores" in {
      val tournamentE = newTournament(4, 2).drawNextRound
      tournamentE must beRight
      val (round, tournament) = tournamentE.right.get
      tournament.currentRound must beSome(round)
      val pairings = round.pairings.keySet
      val rfE = pairings.foldLeft[Either[Round.ValidationError, Round]](Right(round)) {
        case (roundE, p) =>
          roundE.flatMap(_.offerResult(p, p.winPlayer1))
      }
      rfE must beRight
      val rf = rfE.right.get
      rf.isFinished must beTrue
      tournament.offerRound(rf) must beRight
    }

    "not accept an unfinished round to update scores" in {
      newTournament(4, 2).drawNextRound must beRight.like {
        case (round, tournament) =>
          tournament.offerRound(round) must beLeft(RoundNotFinished)
      }
    }

    "not accept a round whose pairings don't match" in {
      newTournament(4, 2).drawNextRound must beRight.like {
        case (_, tournament) =>
          val round = finishRound(newRound(4))
          tournament.offerRound(round) must beLeft(UnexpectedRound)
      }
    }

    "have its state updated accordingly on completed round matches" in {
      newTournament(4, 2).drawNextRound must beRight.like {
        case (round, tournament) =>
          val pairings = round.pairings.keySet.toList
          val resultF = { p: Pairing => Result.Complete(Some(p.player1)) }
          tournament.offerRound(finishRound(round, resultF)) must beRight.like {
            case t =>
              val expectedScores = pairings.foldLeft(Map.empty[String, PlayerScore]) {
                case (m, p) =>
                  m.updated(p.player1, PlayerScore(p.player1, 1, 0, 0, 1516, Vector(Some(p.player2))))
                    .updated(p.player2, PlayerScore(p.player2, 0, 0, 1, 1484, Vector(Some(p.player1))))
              }
              t.scores must containTheSameElementsAs(expectedScores.values.toList)
              t.invalidatedPlayers must beEmpty
          }
      }
    }

    "have its state updated accordingly on errored round matches" in {
      newTournament(4, 2).drawNextRound must beRight.like {
        case (round, tournament) =>
          val pairings = round.pairings.keySet.toList
          val resultF = { p: Pairing =>
            if (p == pairings.head) Result.Error(p.player2)
            else Result.Complete(Some(p.player1))
          }
          tournament.offerRound(finishRound(round, resultF)) must beRight.like {
            case t =>
              val expectedScores = pairings.foldLeft(Map.empty[String, PlayerScore]) {
                case (m, p) =>
                  m.updated(p.player1, PlayerScore(p.player1, 1, 0, 0, 1516, Vector(Some(p.player2))))
                    .updated(p.player2, PlayerScore(p.player2, 0, 0, 1, 1484, Vector(Some(p.player1))))
              }
              t.scores must containTheSameElementsAs(expectedScores.values.toList)
              t.invalidatedPlayers mustEqual Set(pairings.head.player2)
          }
      }
    }

    "have proper pairings in subsequent rounds" in {
      newTournament(4, 2).drawNextRound must beRight.like {
        case (round, tournament) =>
          val pairings = round.pairings.keySet.toList
          tournament.offerRound(finishRound(round, _.winPlayer1)) must beRight.like {
            case t1 =>
              t1.drawNextRound must beRight.like {
                case (nextRound, _) =>
                  nextRound.pairings.keySet must containAnyOf(
                    List(
                      Pairing(pairings(0).player1, pairings(1).player1),
                      Pairing(pairings(1).player1, pairings(0).player1)))
                  nextRound.pairings.keySet must containAnyOf(
                    List(
                      Pairing(pairings(0).player2, pairings(1).player2),
                      Pairing(pairings(1).player2, pairings(0).player2)))
              }
          }
      }
    }

    "have the pairing results filled automatically for invalidated players" in {
      newTournament(4, 2).drawNextRound must beRight.like {
        case (round, tournament) =>
          val pairings = round.pairings.keySet.toList
          val resultF = { p: Pairing =>
            if (p == pairings.head) Result.Error(p.player2)
            else Result.Complete(Some(p.player1))
          }
          tournament.offerRound(finishRound(round, resultF)) must beRight.like {
            case t =>
              t.drawNextRound must beRight.like {
                case (nextRound, _) =>
                  val failingPlayer = pairings.head.player2
                  forall(nextRound.pairings) {
                    case (p, result) => p.players.contains(failingPlayer) mustEqual result.isDefined
                  }
              }
          }
      }
    }

    "be properly finished after all rounds are complete" in {
      val (round1, tournament1) = newTournament(4, 2).drawNextRound.right.get
      val finishedRound1 = finishRound(round1)
      val (round2, tournament2) = tournament1.offerRound(finishedRound1).right.get.drawNextRound.right.get
      val finishedRound2 = finishRound(round2)
      val tournamentF = tournament2.offerRound(finishedRound2).right.get
      tournamentF.isFinished must beTrue
    }

    "properly handle byes with a number of rounds at most equal to the number of players" in {
      val tournament = newTournament(5, 5)
      val (_, rounds) = runTournament(tournament)
      rounds.flatMap(_.bye).toSet mustEqual tournament.players
    }

    "properly handle byes with a number of rounds larger than the number of players" in {
      val tournament = newTournament(5, 10)
      val (_, rounds) = runTournament(tournament)
      forall(rounds.flatMap(_.bye).groupBy(identity).mapValues(_.length)) {
        case (_, n) =>
          n mustEqual 2
      }
    }

    "have aro properly calculated" in {
      val p1, p2, p3, p4 = newPlayer()
      val p1S = PlayerScore(p1, 3, 0, 0, 1500, Vector(Some(p2), Some(p3), Some(p4)))
      val p2S = PlayerScore(p2, 2, 0, 1, 1500, Vector(Some(p1), Some(p3), Some(p4)))
      val p3S = PlayerScore(p3, 1, 0, 2, 1500, Vector(Some(p1), Some(p2), Some(p4)))
      val p4S = PlayerScore(p4, 0, 0, 3, 1500, Vector(Some(p1), Some(p2), Some(p3)))
      val scores = List(p1S, p2S, p3S, p4S)
      p1S.aro(scores) mustEqual 3.0
      p2S.aro(scores) mustEqual 4.0
      p3S.aro(scores) mustEqual 5.0
      p4S.aro(scores) mustEqual 6.0
    }

    "end with 3 of the best 5 players on the top 3 spots" in {
      // We should replace this with numbers from the actual tournament
      val tournament = newTournament(50, 25)
      val victoryTest: (Pairing => Result) = (p: Pairing) =>
        if (p.player1 < p.player2) p.winPlayer1
        else p.winPlayer2
      val (finalTournament, _) = runTournament(tournament, victoryTest)
      val tournamentWinners = finalTournament.scores.map(_.player)
      val expectedWinners = tournament.players.toList.sorted
      tournamentWinners.head === expectedWinners.head
      expectedWinners.take(5) must contain(allOf(tournamentWinners.take(3): _*))
    }

    "properly perform a round-robin tournament with a circle method pairing" in {
      val nPlayers = 50
      val (tournament, _) = runTournament(newTournament(nPlayers, nPlayers - 1, CircleMethodPairing))
      forall(tournament.scores) { s =>
        s.opponents.flatten.size === nPlayers - 1
        s.opponents.flatten.toSet.size === nPlayers - 1
      }

      val (tournament2, _) = runTournament(newTournament(nPlayers, 2 * (nPlayers - 1), CircleMethodPairing))
      forall(tournament2.scores) { s =>
        s.opponents.flatten.size === 2 * (nPlayers - 1)
        forall(s.opponents.flatten.groupBy(identity).mapValues(_.size)) { case (_, s) => s mustEqual 2 }
        s.opponents.flatten.toSet.size === nPlayers - 1
      }
    }
  }
}
