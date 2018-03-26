package eu.shiftforward.suecajudge.tournament

/**
 * The record of a given player in a tournament.
 */
case class PlayerScore(
    player: String,
    wins: Int,
    draws: Int,
    losses: Int,
    elo: Int,
    opponents: Vector[Option[String]] = Vector.empty) {

  def offerResult(result: Result, otherPlayerScore: Option[PlayerScore] = None): PlayerScore = {
    val resultVal = result match {
      case Result.Complete(Some(`player`)) => 1.0
      case Result.Complete(Some(_)) => -1.0
      case Result.Complete(None) => 0.0
      case Result.Error(`player`) => -1.0
      case Result.Error(_) => 1.0
    }

    // Elo calculation based on
    // https://metinmediamath.wordpress.com/2013/11/27/how-to-calculate-the-elo-rating-including-example/
    val nextElo = otherPlayerScore.fold(elo) { p2 =>
      val r1 = math.pow(10.0, elo / 400.0)
      val r2 = math.pow(10.0, p2.elo / 400.0)
      val e1 = r1 / (r1 + r2)
      val s1 = (resultVal + 1.0) / 2.0
      (elo + 32.0 * (s1 - e1)).toInt
    }
    this.copy(
      wins = this.wins + (if (resultVal == 1.0) 1 else 0),
      draws = this.draws + (if (resultVal == 0.0) 1 else 0),
      losses = this.losses + (if (resultVal == -1.0) 1 else 0),
      elo = nextElo,
      opponents = opponents :+ otherPlayerScore.map(_.player))
  }

  def aro(playerScores: List[PlayerScore]): Double = {
    val scoreMap = playerScores.map(s => s.player -> s.score).toMap
    val flatOpponents = opponents.flatten
    if (flatOpponents.isEmpty)
      0.0
    else {
      val opponentSum = flatOpponents.map(scoreMap.getOrElse(_, 0)).sum
      opponentSum.toDouble / flatOpponents.length
    }
  }

  lazy val score: Int =
    wins * PlayerScore.Points.Win + draws * PlayerScore.Points.Draw + losses * PlayerScore.Points.Loss
}

object PlayerScore {
  def empty(p: String) = PlayerScore(p, 0, 0, 0, 1500)

  object Points {
    final val Win = 3
    final val Loss = 0
    final val Draw = 1
  }
}
