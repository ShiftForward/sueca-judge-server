package eu.shiftforward.suecajudge.worker

import scala.io.Source

import org.specs2.mutable.Specification

import eu.shiftforward.suecajudge.game._
import eu.shiftforward.suecajudge.safeexec.codec.Decoder._
import eu.shiftforward.suecajudge.safeexec.codec.Encoder._

class ValidatorSpec extends Specification {
  "A Validator" should {
    "have valid test cases" in {
      val hand1 = List(
        Card(Card.Hearts, 'A'),
        Card(Card.Hearts, '2'),
        Card(Card.Clubs, 'K'),
        Card(Card.Hearts, '6'),
        Card(Card.Spades, '6'),
        Card(Card.Spades, '2'),
        Card(Card.Diamonds, '3'),
        Card(Card.Diamonds, '4'),
        Card(Card.Spades, 'K'),
        Card(Card.Spades, '7'))

      val hand2 = List(
        Card(Card.Hearts, 'K'),
        Card(Card.Hearts, '3'),
        Card(Card.Clubs, 'J'),
        Card(Card.Clubs, '5'),
        Card(Card.Hearts, '7'),
        Card(Card.Spades, '3'),
        Card(Card.Diamonds, '6'),
        Card(Card.Spades, 'J'),
        Card(Card.Clubs, '2'),
        Card(Card.Spades, 'A'))

      val hand3 = List(
        Card(Card.Hearts, 'Q'),
        Card(Card.Hearts, '4'),
        Card(Card.Clubs, 'Q'),
        Card(Card.Clubs, '3'),
        Card(Card.Clubs, '6'),
        Card(Card.Diamonds, 'K'),
        Card(Card.Diamonds, 'Q'),
        Card(Card.Clubs, '7'),
        Card(Card.Diamonds, '7'),
        Card(Card.Diamonds, '5'))

      val hand4 = List(
        Card(Card.Hearts, 'J'),
        Card(Card.Hearts, '5'),
        Card(Card.Clubs, 'A'),
        Card(Card.Clubs, '4'),
        Card(Card.Spades, '5'),
        Card(Card.Spades, '4'),
        Card(Card.Diamonds, 'A'),
        Card(Card.Diamonds, 'J'),
        Card(Card.Diamonds, '2'),
        Card(Card.Spades, 'Q'))

      val hands: List[Card] = List(hand1, hand2, hand3, hand4).flatten
      hands.distinct.size must be_==(hands.size)

      var g = Game(
        nextPlayer = Some(getPlayerAfter(3)),
        hands = Vector(hand1, hand2, hand3, hand4),
        trumpPlayer = 3,
        trump = Card(Card.Diamonds, '2'),
        currentTrick = Vector.fill(4)(None),
        trickSuit = None,
        previousTricks = Nil,
        score = Vector.fill(2)(0))

      forall(0 until 27) { _ =>
        val player = g.nextPlayer.get
        val card = g.hands(player).head
        g.play(card) must beRight.which { x =>
          g = x
          ok
        }
      }
      val expected = PartialGame(g, g.nextPlayer.get)
      val actual = Source.fromFile("src/main/resources/testcases/7th_trick_user_has_suit.txt").mkString
      actual.decode[PartialGame].get must be_==(expected)
      actual must be_==(expected.encode)
    }

    "have valid test cases 2" in {
      val hand1 = List(
        Card(Card.Hearts, 'A'),
        Card(Card.Hearts, '2'),
        Card(Card.Clubs, 'K'),
        Card(Card.Hearts, '6'),
        Card(Card.Spades, '6'),
        Card(Card.Spades, '2'),
        Card(Card.Diamonds, '3'),
        Card(Card.Diamonds, '4'),
        Card(Card.Diamonds, '6'),
        Card(Card.Spades, '7'))

      val hand2 = List(
        Card(Card.Hearts, 'K'),
        Card(Card.Hearts, '3'),
        Card(Card.Clubs, 'J'),
        Card(Card.Clubs, '5'),
        Card(Card.Hearts, '7'),
        Card(Card.Spades, '3'),
        Card(Card.Spades, 'K'),
        Card(Card.Spades, 'J'),
        Card(Card.Clubs, '2'),
        Card(Card.Spades, 'A'))

      val hand3 = List(
        Card(Card.Hearts, 'Q'),
        Card(Card.Hearts, '4'),
        Card(Card.Clubs, 'Q'),
        Card(Card.Clubs, '3'),
        Card(Card.Clubs, '6'),
        Card(Card.Diamonds, 'K'),
        Card(Card.Diamonds, 'Q'),
        Card(Card.Clubs, '7'),
        Card(Card.Diamonds, '7'),
        Card(Card.Diamonds, '5'))

      val hand4 = List(
        Card(Card.Hearts, 'J'),
        Card(Card.Hearts, '5'),
        Card(Card.Clubs, 'A'),
        Card(Card.Clubs, '4'),
        Card(Card.Spades, '5'),
        Card(Card.Spades, '4'),
        Card(Card.Diamonds, 'A'),
        Card(Card.Diamonds, 'J'),
        Card(Card.Diamonds, '2'),
        Card(Card.Spades, 'Q'))

      val hands: List[Card] = List(hand1, hand2, hand3, hand4).flatten
      hands.distinct.size must be_==(hands.size)

      var g = Game(
        nextPlayer = Some(getPlayerAfter(3)),
        hands = Vector(hand1, hand2, hand3, hand4),
        trumpPlayer = 3,
        trump = Card(Card.Diamonds, '2'),
        currentTrick = Vector.fill(4)(None),
        trickSuit = None,
        previousTricks = Nil,
        score = Vector.fill(2)(0))

      forall(0 until 27) { _ =>
        val player = g.nextPlayer.get
        val card = g.hands(player).head
        g.play(card) must beRight.which { x =>
          g = x
          ok
        }
      }
      val expected = PartialGame(g, g.nextPlayer.get)
      val actual = Source.fromFile("src/main/resources/testcases/7th_trick_user_doesnt_have_suit.txt").mkString
      actual.decode[PartialGame].get must be_==(expected)
      actual must be_==(expected.encode)
    }
  }
}
