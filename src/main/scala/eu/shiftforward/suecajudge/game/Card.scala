package eu.shiftforward.suecajudge.game

import scala.util.Random

import io.circe._
import io.circe.parser.decode

import eu.shiftforward.suecajudge.game.Card._

case class Card(suit: Suit, value: Char) {
  override def toString = value + suit.toString
}

object Card {
  sealed trait Suit
  case object Clubs extends Suit { override def toString = "♣" }
  case object Diamonds extends Suit { override def toString = "♦" }
  case object Hearts extends Suit { override def toString = "♥" }
  case object Spades extends Suit { override def toString = "♠" }

  val allSuits = List(Clubs, Diamonds, Hearts, Spades)

  object Suit {
    implicit val suitEncoder: Encoder[Suit] = Encoder[String].contramap(_.toString)
    implicit val suitDecoder: Decoder[Suit] = Decoder[String].emap { s =>
      allSuits.find(_.toString == s) match {
        case Some(suit) => Right(suit)
        case None => Left(s"Unknown suit $s.")
      }
    }
  }

  val allValues = ('2' to '7') ++ List('Q', 'J', 'K', 'A')

  val deck: List[Card] = {
    for {
      suit <- allSuits
      value <- allValues
    } yield Card(suit, value)
  }

  def shuffledDeck(random: Random = Random): List[Card] =
    random.shuffle(deck)

  implicit val cardEncoder: Encoder[Card] = Encoder[String].contramap(_.toString)
  implicit val cardDecoder: Decoder[Card] = Decoder[String].emap { s =>
    if (s.size != 2)
      Left(s"Unknown card $s.")
    else
      decode[Suit]("" + s(1)).left.map(_.getMessage).right.map(Card(_, s(0)))
  }
}
