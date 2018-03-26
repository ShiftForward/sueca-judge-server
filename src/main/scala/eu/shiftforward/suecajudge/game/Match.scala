package eu.shiftforward.suecajudge.game

import scala.util.Random

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

import eu.shiftforward.suecajudge.game.Match._

case class Match(previousGames: List[Game], score: Vector[Int]) {

  def isFinished: Boolean =
    previousGames.length == NumberOfGames

  def winner: Option[Int] = {
    if (score(0) == score(1)) None
    else Some(if (score(0) > score(1)) 0 else 1)
  }

  def drawNextGame(random: Random = Random): Either[ValidationError, Game] = {
    if (isFinished) Left(MatchAlreadyFinished)
    else Right(Game.newGame(previousGames.headOption, random))
  }

  /**
   * Offers the provided result for a pairing to the current round.
   */
  def offerGame(game: Game): Either[ValidationError, Match] = {
    if (isFinished) Left(MatchAlreadyFinished)
    else if (!game.isFinished) Left(GameNotFinished)
    else {
      val newScore = game.score(0) match {
        case 120 => score.updated(0, score(0) + 4)
        case n if n > 90 => score.updated(0, score(0) + 2)
        case n if n > 60 => score.updated(0, score(0) + 1)
        case 60 => Vector(score(0) + 1, score(1) + 1)
        case n if n >= 30 => score.updated(1, score(1) + 1)
        case n if n > 0 => score.updated(1, score(1) + 2)
        case 0 => score.updated(1, score(1) + 4)
      }
      Right(copy(previousGames = game :: previousGames, score = newScore))
    }
  }
}

object Match {
  val NumberOfGames = 5

  /**
   * The result of producing an invalid state on the match API.
   */
  sealed trait ValidationError
  case object MatchAlreadyFinished extends ValidationError
  case object GameNotFinished extends ValidationError

  def newMatch() = Match(Nil, Vector(0, 0))

  implicit val matchEncoder: Encoder[Match] = deriveEncoder[Match]
  implicit val matchDecoder: Decoder[Match] = deriveDecoder[Match]
}
