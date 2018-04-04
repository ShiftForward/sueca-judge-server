package eu.shiftforward.suecajudge.safeexec.codec

import eu.shiftforward.suecajudge.game.{ Card, PartialGame }
import eu.shiftforward.suecajudge.game.Card._

sealed trait Decoder[T] {
  def decode(string: String): Option[T]
}

object Decoder {
  implicit class DecoderOps(string: String) {
    def decode[T: Decoder]: Option[T] = implicitly[Decoder[T]].decode(string)
  }

  object ValueDecoder extends Decoder[Char] {
    val validValues = Card.allValues.map(_.toString).toSet
    override def decode(string: String): Option[Char] =
      if (validValues.contains(string)) string.headOption
      else None
  }

  implicit object SuitDecoder extends Decoder[Suit] {
    override def decode(string: String): Option[Suit] = string match {
      case "C" => Some(Clubs)
      case "D" => Some(Diamonds)
      case "H" => Some(Hearts)
      case "S" => Some(Spades)
      case _ => None
    }
  }

  implicit object CardDecoder extends Decoder[Card] {
    override def decode(string: String): Option[Card] = {
      val (valueString, suitString) = string.splitAt(1)
      for {
        value <- ValueDecoder.decode(valueString)
        suit <- suitString.decode[Suit]
      } yield Card(suit, value)
    }
  }

  implicit object PartialGameDecoder extends Decoder[PartialGame] {
    override def decode(string: String): Option[PartialGame] = {
      def decodePreviousTricks(s: String): List[(Int, Vector[Card])] =
        s.split(" ").toList.drop(1).grouped(5).map {
          case (a :: rest) => (a.toInt, rest.map(_.decode[Card].get).toVector)
          case _ => throw new IllegalArgumentException("Illegal PartialGame")
        }.toList

      val lines = string.lines
      Some(new PartialGame(
        Some(lines.next().toInt),
        lines.next().split(" ").drop(1).map(_.decode[Card].get).toList,
        lines.next().toInt,
        lines.next().decode[Card].get,
        lines.next().split(" ").drop(1).map(_.decode[Card]).toVector,
        lines.next().decode[Suit],
        decodePreviousTricks(lines.next()).reverse,
        lines.next().split(" ").map(_.toInt).toVector))
    }
  }
}

