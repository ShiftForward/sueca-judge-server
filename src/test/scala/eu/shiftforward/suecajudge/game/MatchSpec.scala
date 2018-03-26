package eu.shiftforward.suecajudge.game

import org.specs2.mutable.Specification

import eu.shiftforward.suecajudge.game.Match._

class MatchSpec extends Specification {

  def finishedGame(score1: Int, score2: Int): Game = {
    Game.newGame().copy(nextPlayer = None, score = Vector(score1, score2))
  }

  "A Match" should {

    "start with a correct initial state" in {
      Match.newMatch() ==== Match(Nil, Vector(0, 0))
    }

    "provide a way to know whether it is finished" in {
      Match(Nil, Vector(0, 0)).isFinished must beFalse
      Match(List.fill(NumberOfGames - 1)(Game.newGame()), Vector(0, 0)).isFinished must beFalse
      Match(List.fill(NumberOfGames)(Game.newGame()), Vector(0, 0)).isFinished must beTrue
    }

    "provide a way to know its winner, if available" in {
      Match(Nil, Vector(0, 0)).winner must beNone
      Match(Nil, Vector(10, 5)).winner must beSome(0)
      Match(Nil, Vector(5, 10)).winner must beSome(1)
      Match(Nil, Vector(10, 10)).winner must beNone
    }

    "allow drawing new games based on previous ones" in {
      Match.newMatch().drawNextGame() must beRight

      val lastGame = Game.newGame()
      Match(lastGame :: Nil, Vector(1, 0)).drawNextGame() must beRight.which { game =>
        game.trumpPlayer ==== getPlayerAfter(lastGame.trumpPlayer)
      }
    }

    "reject drawing new games when finished" in {
      Match(List.fill(NumberOfGames)(Game.newGame()), Vector(5, 5)).drawNextGame() must beLeft(MatchAlreadyFinished)
    }

    "accept finished games, updating its score correctly" in {
      Match.newMatch().offerGame(finishedGame(65, 55)) must beRight.which(_.score ==== Vector(1, 0))
      Match.newMatch().offerGame(finishedGame(50, 70)) must beRight.which(_.score ==== Vector(0, 1))
      Match.newMatch().offerGame(finishedGame(60, 60)) must beRight.which(_.score ==== Vector(1, 1))
      Match.newMatch().offerGame(finishedGame(90, 30)) must beRight.which(_.score ==== Vector(1, 0))
      Match.newMatch().offerGame(finishedGame(30, 90)) must beRight.which(_.score ==== Vector(0, 1))
      Match.newMatch().offerGame(finishedGame(91, 29)) must beRight.which(_.score ==== Vector(2, 0))
      Match.newMatch().offerGame(finishedGame(29, 91)) must beRight.which(_.score ==== Vector(0, 2))
      Match.newMatch().offerGame(finishedGame(120, 0)) must beRight.which(_.score ==== Vector(4, 0))
      Match.newMatch().offerGame(finishedGame(0, 120)) must beRight.which(_.score ==== Vector(0, 4))

      Match(Nil, Vector(2, 3)).offerGame(finishedGame(65, 55)) must beRight.which(_.score ==== Vector(3, 3))
      Match(Nil, Vector(5, 7)).offerGame(finishedGame(50, 70)) must beRight.which(_.score ==== Vector(5, 8))
      Match(Nil, Vector(5, 6)).offerGame(finishedGame(60, 60)) must beRight.which(_.score ==== Vector(6, 7))
    }

    "reject games that are not finished" in {
      Match.newMatch().offerGame(Game.newGame()) must beLeft(GameNotFinished)
    }

    "reject games when finished" in {
      Match(List.fill(NumberOfGames)(Game.newGame()), Vector(5, 5)).offerGame(finishedGame(65, 55)) must beLeft(MatchAlreadyFinished)
    }
  }
}
