package eu.shiftforward.suecajudge.tournament

import scala.util.Random

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

import eu.shiftforward.suecajudge.tournament.SwissTournament._

/**
 * A tournament based on the swiss system.
 */
case class SwissTournament(
    totalRounds: Int,
    players: Set[String],
    rounds: List[Round],
    invalidatedPlayers: Set[String],
    pairingStrategy: PairingStrategy) {

  /**
   * Random score assignment used for tie breakers
   */
  lazy val randomScores: Map[String, Double] = players.map(p => p -> Random.nextDouble()).toMap

  /**
   * The current round number.
   */
  lazy val currentRoundNumber = rounds.length

  /**
   * The round that is currently running.
   */
  lazy val currentRound = rounds.headOption

  /**
   * Whether the tournament is finished.
   */
  lazy val isFinished: Boolean = rounds.length == totalRounds && currentRound.fold(false)(_.isFinished)

  /**
   * Whether the tournament has started.
   */
  lazy val isStarted: Boolean = rounds.nonEmpty

  /**
   * Whether the tournament requires a bye in its rounds (i.e. the number of players is odd).
   */
  lazy val needsBye: Boolean = scores.size % 2 != 0

  /**
   * The result for a given bye.
   */
  def resultForBye(p: String): Result = Result.Complete(Some(p))

  /**
   * Draws the next round, if the current one is already finished, returning a new tournament correctly updated.
   */
  lazy val drawNextRound: Either[ValidationError, (Round, SwissTournament)] =
    if (isFinished)
      Left(TournamentAlreadyFinished)
    else if (!currentRound.fold(true)(_.isFinished))
      Left(RoundNotFinished)
    else {
      // Create pairings and calculate the next bye for the round.
      val (nextPairings, nextBye) = pairingStrategy.pair(currentRoundNumber, scores.toVector)

      // Create round. Pairings with invalidated players will have their results already filled.
      val nextRound = Round(nextPairings.map {
        case p @ Pairing(p1, p2) if invalidatedPlayers(p1) && invalidatedPlayers(p2) => p -> Some(p.draw)
        case p @ Pairing(p1, _) if invalidatedPlayers(p1) => p -> Some(p.winPlayer2)
        case p @ Pairing(_, p2) if invalidatedPlayers(p2) => p -> Some(p.winPlayer1)
        case p => p -> None
      }.toMap, nextBye)

      Right((nextRound, this.copy(rounds = nextRound :: this.rounds)))
    }

  /**
   * Offers the current finished round to the tournament, updating the scores for the tournament.
   */
  def offerRound(round: Round): Either[ValidationError, SwissTournament] =
    if (isFinished)
      Left(TournamentAlreadyFinished)
    else if (!isStarted)
      Left(TournamentNotYetStarted)
    else if (!round.isFinished)
      Left(RoundNotFinished)
    else if (round.pairings.keySet != currentRound.get.pairings.keySet || round.bye != currentRound.get.bye)
      Left(UnexpectedRound)
    else {
      val newInvalidatedPlayers = round.pairings.values.collect { case Some(Result.Error(p)) => p }
      Right(this.copy(
        rounds = round :: rounds.drop(1),
        invalidatedPlayers = invalidatedPlayers ++ newInvalidatedPlayers))
    }

  lazy val scores: List[PlayerScore] = {
    val scoresMap = players.map(p => p -> PlayerScore.empty(p)).toMap
    val scores = rounds.reverse.foldLeft(scoresMap) {
      case (scores, round) =>
        val scoresWithBye = round.bye match {
          case Some(player) => scores.updated(player, scores(player).offerResult(resultForBye(player)))
          case None => scores
        }
        round.pairings.foldLeft(scoresWithBye) {
          case (scores, (pairing, result)) =>
            result match {
              case Some(result) =>
                val p1prevScores = scores(pairing.player1)
                val p2prevScores = scores(pairing.player2)

                scores
                  .updated(pairing.player1, p1prevScores.offerResult(result, Some(p2prevScores)))
                  .updated(pairing.player2, p2prevScores.offerResult(result, Some(p1prevScores)))

              case None =>
                scores
            }
        }
    }.values.toList

    if (isFinished) {
      // Sorts players by decreasing score. Use ARO to break ties. Shuffle players with the same score and ARO.
      scores.sorted(Ordering.fromLessThan[PlayerScore] {
        case (s1, s2) =>
          if (s1.score == s2.score) {
            val aro1 = s1.aro(scores)
            val aro2 = s2.aro(scores)
            if (aro1 == aro2)
              randomScores(s1.player) < randomScores(s2.player)
            else
              aro1 > aro2
          } else
            s1.score > s2.score
      })
    } else {
      // Sorts players by decreasing score. Shuffle players within the same score group.
      scores.sorted(Ordering.fromLessThan[PlayerScore] {
        case (s1, s2) =>
          if (s1.score == s2.score)
            randomScores(s1.player) < randomScores(s2.player)
          else
            s1.score > s2.score
      })
    }
  }
}

object SwissTournament {
  def apply(players: Set[String], rounds: Int, pairingStrategy: PairingStrategy = MaximumWeightMatchingPairing): SwissTournament =
    SwissTournament(
      rounds,
      players,
      Nil,
      Set.empty,
      pairingStrategy)

  /**
   * The result of producing an invalid state on the tournament API.
   */
  sealed trait ValidationError
  case object TournamentAlreadyFinished extends ValidationError
  case object TournamentNotYetStarted extends ValidationError
  case object UnexpectedRound extends ValidationError
  case object RoundNotFinished extends ValidationError

  implicit val tournamentEncoder: Encoder[SwissTournament] = deriveEncoder[SwissTournament]
  implicit val tournamentDecoder: Decoder[SwissTournament] = deriveDecoder[SwissTournament]
}
