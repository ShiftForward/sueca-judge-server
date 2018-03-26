package eu.shiftforward.suecajudge

package object game {

  def cardValue(card: Card): Int = card.value match {
    case 'A' => 9
    case '7' => 8
    case 'K' => 7
    case 'J' => 6
    case 'Q' => 5
    case ch => ch - '2'
  }

  def cardPoints(card: Card): Int = card.value match {
    case 'A' => 11
    case '7' => 10
    case 'K' => 4
    case 'J' => 3
    case 'Q' => 2
    case _ => 0
  }

  def getTeam(player: Int): Int =
    player % 2

  def getPlayerAfter(player: Int): Int =
    (player + 1) % 4
}
