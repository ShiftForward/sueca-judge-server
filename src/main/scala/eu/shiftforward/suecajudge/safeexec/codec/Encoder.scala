package eu.shiftforward.suecajudge.safeexec.codec

import eu.shiftforward.suecajudge.game.Card._
import eu.shiftforward.suecajudge.game.{ Card, PartialGame }

sealed trait Encoder[T] {
  def encode(obj: T): String
}

object Encoder {
  implicit class EncoderOps[T: Encoder](obj: T) {
    def encode: String = implicitly[Encoder[T]].encode(obj)
  }

  implicit def listEncoder[T: Encoder]: Encoder[List[T]] = new Encoder[List[T]] {
    override def encode(seq: List[T]): String =
      seq.length + (if (seq.length > 0) " " + seq.map(_.encode).mkString(" ") else "")
  }

  implicit def vectorEncoder[T: Encoder]: Encoder[Vector[T]] = new Encoder[Vector[T]] {
    override def encode(vec: Vector[T]): String = vec.map(_.encode).mkString(" ")
  }

  implicit def pairEncoder[T: Encoder, U: Encoder]: Encoder[(T, U)] = new Encoder[(T, U)] {
    override def encode(pair: (T, U)): String = pair._1.encode + " " + pair._2.encode
  }

  implicit def optionEncoder[T: Encoder]: Encoder[Option[T]] = new Encoder[Option[T]] {
    override def encode(opt: Option[T]): String = opt match {
      case None => "X"
      case Some(x) => x.encode
    }
  }

  implicit def numericEncoder[T: Numeric]: Encoder[T] = new Encoder[T] {
    override def encode(value: T): String = value.toString
  }

  implicit object SuitEncoder extends Encoder[Suit] {
    override def encode(suit: Suit): String = suit match {
      case Clubs => "C"
      case Diamonds => "D"
      case Hearts => "H"
      case Spades => "S"
    }
  }

  implicit object CardEncoder extends Encoder[Card] {
    override def encode(card: Card): String = card.value + card.suit.encode
  }

  implicit object PartialGameEncoder extends Encoder[PartialGame] {
    override def encode(game: PartialGame): String = {
      List(
        game.nextPlayer.encode,
        game.hand.encode,
        game.trumpPlayer.encode,
        game.trump.encode,
        ((0 until 4).find { i =>
          game.currentTrick(i).isDefined && game.currentTrick((i + 4 - 1) % 4).isEmpty
        }.getOrElse(game.nextPlayer.get), game.currentTrick).encode,
        game.trickSuit.encode,
        game.previousTricks.reverse.encode,
        game.score.encode).mkString("", "\n", "\n")
    }
  }
}
