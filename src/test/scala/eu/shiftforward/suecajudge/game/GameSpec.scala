package eu.shiftforward.suecajudge.game

import org.specs2.execute.SkipException
import org.specs2.mutable.Specification

import eu.shiftforward.suecajudge.game.Game._

class GameSpec extends Specification {

  def validCard(st: Game): Card = st.validCards.head

  def invalidCard(st: Game): Option[Card] = {
    st.hands(st.nextPlayer.get).partition { c => st.trickSuit.contains(c.suit) } match {
      case (Nil, _) => None
      case (_, nonSameSuit) => Some(nonSameSuit.head)
    }
  }

  def quickPlay(st: Game, n: Int): Game = {
    (1 to n).foldLeft(st) { (cst, _) => cst.play(validCard(cst)).right.get }
  }

  "A Sueca game" should {

    "start with a correct initial state" in {

      def baseCheckState(st: Game) = {
        st.nextPlayer must beSome((st.trumpPlayer + 1) % 4)
        forall(st.hands) { hand => hand.length ==== 10 }
        st.hands.flatten.toSet.size ==== 40
        st.hands(st.trumpPlayer) must contain(st.trump)
        st.previousTricks ==== Nil
        st.currentTrick ==== Vector(None, None, None, None)
        st.trickSuit ==== None
        st.score ==== Vector(0, 0)
        st.isFinished must beFalse
      }

      val st = Game.newGame()
      st.trumpPlayer must beBetween(0, 3)
      baseCheckState(st)

      val st2 = Game.newGame(Some(st))
      st2.trumpPlayer ==== (st.trumpPlayer + 1) % 4
      baseCheckState(st2)
    }

    "update correctly the state when valid moves are made" in {

      var st = Game.newGame()
      forall(0 until 40) { turn =>
        st.isFinished must beFalse

        val player = st.nextPlayer.get
        val card = validCard(st)

        st.play(card) must beRight.which { newSt =>
          st = newSt
          st.hands(player) must not(contain(card))

          if (turn % 4 != 3) {
            st.nextPlayer must beSome((player + 1) % 4)
            st.currentTrick(player) must beSome(card)
          } else {
            st.previousTricks must not(beEmpty)
            st.currentTrick ==== Vector(None, None, None, None)
          }
        }
      }

      st.nextPlayer must beNone
      st.isFinished must beTrue
    }

    "reject moves where a card not in the player's hand is played" in {
      val st = Game.newGame()
      val illegalCard = st.hands((st.nextPlayer.get + 1) % 4).head
      st.play(illegalCard) must beLeft(IllegalCard)
    }

    "reject moves where a card not from the trick's suit is played" in {
      def findInvalidMove(st: Game): (Game, Card) = invalidCard(st) match {
        case Some(card) => (st, card)
        case _ if !st.isFinished => findInvalidMove(st.play(validCard(st)).right.get)
        case _ => throw SkipException(skipped) // this can happen, but requires an insanely rare card distribution
      }

      val (st, illegalCard) = findInvalidMove(Game.newGame())
      st.play(illegalCard) must beLeft(IllegalCard)
    }

    "reject moves on a finished game" in {
      val st = quickPlay(Game.newGame(), 40)
      st.play(Card.deck.head) must beLeft(GameAlreadyFinished)
    }

    "update correctly the score after a move" in {
      val st = quickPlay(Game.newGame(), 4)

      val trickPoints = st.previousTricks.head._2.collect {
        case Card(_, 'A') => 11
        case Card(_, '7') => 10
        case Card(_, 'K') => 4
        case Card(_, 'J') => 3
        case Card(_, 'Q') => 2
      }.sum

      st.score must containTheSameElementsAs(Vector(0, trickPoints))

      val st1 = quickPlay(st, 36)
      st1.score.sum ==== 120
    }

    "correctly compute starting players for previous tricks" in {
      val g = Game.newGame()
      val startingPlayer = g.nextPlayer.get
      val oneTrickLater = quickPlay(g, 4)
      oneTrickLater.previousTricks.head._1 === startingPlayer
    }
  }
}
