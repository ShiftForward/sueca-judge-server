package eu.shiftforward.suecajudge.game

import scala.util.Random

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

import eu.shiftforward.suecajudge.game.Card._
import eu.shiftforward.suecajudge.game.Game._

case class Game(
    nextPlayer: Option[Int],
    hands: Vector[List[Card]],
    trumpPlayer: Int,
    trump: Card,
    currentTrick: Vector[Option[Card]],
    trickSuit: Option[Suit],
    previousTricks: List[(Int, Vector[Card])],
    score: Vector[Int]) {

  def isFinished: Boolean =
    nextPlayer.isEmpty

  def play(card: Card): Either[ValidationError, Game] = nextPlayer match {
    case Some(player) =>
      if (!canPlay(player, card)) Left(IllegalCard)
      else Right {
        val newState = copy(
          nextPlayer = Some(getPlayerAfter(player)),
          hands = hands.updated(player, hands(player).filterNot(_ == card)),
          currentTrick = currentTrick.updated(player, Some(card)),
          trickSuit = trickSuit.orElse(Some(card.suit)))

        if (currentTrick(getPlayerAfter(player)).isEmpty) newState
        else newState.endTrick
      }
    case None => Left(GameAlreadyFinished)
  }

  def validCards: List[Card] = nextPlayer match {
    case None => Nil
    case Some(player) => hands(player).filter(c => trickSuit.contains(c.suit)) match {
      case Nil => hands(player)
      case h => h
    }
  }

  private def canPlay(player: Int, card: Card): Boolean = {
    hands(player).contains(card) && trickSuit.forall { tsuit =>
      card.suit == tsuit || hands(player).forall(_.suit != tsuit)
    }
  }

  private def endTrick: Game = {
    def suitWeight(suit: Suit): Int =
      if (suit == trump.suit) 100
      else if (suit == trickSuit.get) 0
      else -100

    val trick = currentTrick.flatten
    val (_, winnerPlayer) = trick.zipWithIndex.maxBy {
      case (card, _) => cardValue(card) + suitWeight(card.suit)
    }
    val winnerTeam = getTeam(winnerPlayer)
    val newScore = score.updated(winnerTeam, score(winnerTeam) + trick.map(cardPoints).sum)

    copy(
      nextPlayer = if (hands(0).isEmpty) None else Some(winnerPlayer),
      trickSuit = None,
      currentTrick = Vector.fill(4)(None),
      previousTricks = (nextPlayer.get, trick) :: previousTricks,
      score = newScore)
  }
}

object Game {

  /**
   * The result of producing an invalid state on the game API.
   */
  sealed trait ValidationError
  case object IllegalCard extends ValidationError
  case object GameAlreadyFinished extends ValidationError

  def newGame(lastGame: Option[Game] = None, random: Random = Random): Game = {
    val hands = shuffledDeck(random).grouped(10).toVector
    val trumpPlayer = lastGame.fold(random.nextInt(4)) { st => getPlayerAfter(st.trumpPlayer) }

    Game(
      nextPlayer = Some(getPlayerAfter(trumpPlayer)),
      hands = hands,
      trumpPlayer = trumpPlayer,
      trump = hands(trumpPlayer).head,
      currentTrick = Vector.fill(4)(None),
      trickSuit = None,
      previousTricks = Nil,
      score = Vector.fill(2)(0))
  }

  implicit val gameEncoder: Encoder[Game] = deriveEncoder[Game]
  implicit val gameDecoder: Decoder[Game] = deriveDecoder[Game]
}
