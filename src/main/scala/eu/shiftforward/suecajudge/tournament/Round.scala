package eu.shiftforward.suecajudge.tournament

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

import eu.shiftforward.suecajudge.tournament.Round._

/**
 * Results of a match between players.
 */
sealed trait Result
object Result {
  case class Complete(winner: Option[String]) extends Result
  case class Error(player: String) extends Result

  implicit val resultEncoder: Encoder[Result] = deriveEncoder[Result]
  implicit val resultDecoder: Decoder[Result] = deriveDecoder[Result]
}

/**
 * A pairing between two players in a round.
 */
case class Pairing(player1: String, player2: String) {
  require(player1 != player2)

  /**
   * The set of players involved in the pairing.
   */
  lazy val players: Set[String] = Set(player1, player2)

  lazy val winPlayer1: Result = Result.Complete(Some(player1))
  lazy val winPlayer2: Result = Result.Complete(Some(player2))
  lazy val draw: Result = Result.Complete(None)
  lazy val errorPlayer1: Result = Result.Error(player1)
  lazy val errorPlayer2: Result = Result.Error(player2)
}

object Pairing {
  implicit val pairingEncoder: Encoder[Pairing] = deriveEncoder[Pairing]
  implicit val pairingDecoder: Decoder[Pairing] = deriveDecoder[Pairing]
}

/**
 * A collection of pairings between players in a tournament.
 */
case class Round(pairings: Map[Pairing, Option[Result]], bye: Option[String]) {
  /**
   * Whether the round is finished (i.e. all pairings have results).
   */
  lazy val isFinished: Boolean = pairings.values.forall(_.isDefined)

  /**
   * Offers the provided result for a pairing to the current round.
   */
  def offerResult(pairing: Pairing, result: Result): Either[ValidationError, Round] =
    pairings.get(pairing) match {
      case Some(Some(_)) => Left(ResultAlreadyRecorded)
      case None => Left(UnexpectedPairing)
      case _ => Right(this.copy(pairings.updated(pairing, Some(result))))
    }
}

object Round {

  /**
   * The result of producing an invalid state on the round API.
   */
  sealed trait ValidationError
  case object UnexpectedPairing extends ValidationError
  case object ResultAlreadyRecorded extends ValidationError

  implicit val pairingsEncoder: Encoder[Map[Pairing, Option[Result]]] =
    Encoder[List[(Pairing, Option[Result])]].contramap(_.toList)
  implicit val pairingsDecoder: Decoder[Map[Pairing, Option[Result]]] =
    Decoder[List[(Pairing, Option[Result])]].map(_.toMap)

  implicit val roundEncoder: Encoder[Round] = deriveEncoder[Round]
  implicit val roundDecoder: Decoder[Round] = deriveDecoder[Round]
}
