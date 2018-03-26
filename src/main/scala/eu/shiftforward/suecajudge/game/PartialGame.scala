package eu.shiftforward.suecajudge.game

import eu.shiftforward.suecajudge.game.Card.Suit

case class PartialGame(
    nextPlayer: Option[Int],
    hand: List[Card],
    trumpPlayer: Int,
    trump: Card,
    currentTrick: Vector[Option[Card]],
    trickSuit: Option[Suit],
    previousTricks: List[(Int, Vector[Card])],
    score: Vector[Int])

object PartialGame {
  def apply(st: Game, player: Int): PartialGame = PartialGame(
    nextPlayer = st.nextPlayer,
    hand = st.hands(player),
    trumpPlayer = st.trumpPlayer,
    trump = st.trump,
    currentTrick = st.currentTrick,
    trickSuit = st.trickSuit,
    previousTricks = st.previousTricks,
    score = st.score)
}
