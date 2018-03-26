package eu.shiftforward.suecajudge.safeexec.codec

import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

import eu.shiftforward.suecajudge.game._
import eu.shiftforward.suecajudge.safeexec.codec.Encoder._
import eu.shiftforward.suecajudge.safeexec.codec.Decoder._
import eu.shiftforward.suecajudge.game.Card._

class CodecSpec extends Specification {

  def roundtrip[T: Encoder: Decoder](collection: Seq[T]): MatchResult[Seq[T]] = {
    collection must contain { x: T => x.encode.decode[T] must beSome(x) }.forall
  }

  "An Encoder/Decoder" should {

    "be able to encode/decode suits" in {
      roundtrip[Suit](Card.allSuits)
    }

    "be able to encode/decode cards" in {
      roundtrip[Card](Card.deck)
    }

    "be able to encode a partial game" in {
      val example = new PartialGame(
        nextPlayer = Some(1),
        hand = Card.deck.take(9),
        trumpPlayer = 1,
        trump = Card.deck.head,
        currentTrick = Vector(None, None, None, Card.deck.drop(10).headOption),
        trickSuit = Some(Card.deck.drop(10).head.suit),
        previousTricks = List((1, Card.deck.slice(20, 24).toVector)),
        score = Vector(0, 0))

      example.encode ===
        """1
          |9 2C 3C 4C 5C 6C 7C QC JC KC
          |1
          |2C
          |3 X X X 2D
          |D
          |1 1 2H 3H 4H 5H
          |0 0
          |""".stripMargin
    }

    "be able to decode a partial game" in {
      val example = new PartialGame(
        nextPlayer = Some(2),
        hand = Card.deck.take(8),
        trumpPlayer = 1,
        trump = Card.deck.head,
        currentTrick = Vector(None, None, Card.deck.drop(11).headOption, Card.deck.drop(10).headOption),
        trickSuit = Some(Card.deck.drop(10).head.suit),
        previousTricks = List((2, Card.deck.slice(20, 24).toVector), (1, Card.deck.slice(30, 34).toVector)),
        score = Vector(0, 0))

      """2
          |8 2C 3C 4C 5C 6C 7C QC JC
          |1
          |2C
          |2 X X 3D 2D
          |D
          |2 1 2S 3S 4S 5S 2 2H 3H 4H 5H
          |0 0
          |""".stripMargin.decode[PartialGame] must beSome(example)
    }
  }
}
