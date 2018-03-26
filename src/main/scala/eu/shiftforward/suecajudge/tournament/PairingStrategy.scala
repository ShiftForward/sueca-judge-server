package eu.shiftforward.suecajudge.tournament

import scala.collection.JavaConverters._

import io.circe._
import io.circe.generic.semiauto._
import org.jgrapht.Graphs
import org.jgrapht.alg.matching.PathGrowingWeightedMatching
import org.jgrapht.graph.{ DefaultWeightedEdge, SimpleWeightedGraph }

sealed trait PairingStrategy {
  def pair(roundNumber: Int, scores: Vector[PlayerScore]): (List[Pairing], Option[String])
}

object AdjacentPairing extends PairingStrategy {
  def pair(roundNumber: Int, scores: Vector[PlayerScore]): (List[Pairing], Option[String]) = {
    val nextBye = PairingStrategy.nextBye(scores)
    (scores.filterNot(s => nextBye.contains(s.player)).grouped(2).map(p => Pairing(p(0).player, p(1).player)).toList, nextBye)
  }
}

object MaximumWeightMatchingPairing extends PairingStrategy {
  def pair(roundNumber: Int, scores: Vector[PlayerScore]): (List[Pairing], Option[String]) = {
    val nextBye = PairingStrategy.nextBye(scores)
    val scoresToPair = scores.filterNot(s => nextBye.contains(s.player))
    val scoresMap = scoresToPair.map(s => s.player -> s).toMap

    val g = new SimpleWeightedGraph[String, DefaultWeightedEdge](classOf[DefaultWeightedEdge])
    Graphs.addAllVertices(g, scoresMap.keySet.asJava)
    scoresToPair.indices.foreach { i =>
      ((i + 1) until scoresToPair.size).foreach { j =>
        val p1 = scoresToPair(i).player
        val p2 = scoresToPair(j).player
        val havePlayedBeforePenalty = scoresMap(p1).opponents.count(_ == Some(p2)) * 1000000
        val inDifferentGroupPenalty = math.abs(scoresMap(p1).score - scoresMap(p2).score) * 1000
        // The library doesn't play well with negative numbers.
        val score = 100000000 - (havePlayedBeforePenalty + inDifferentGroupPenalty)
        Graphs.addEdge(g, p1, p2, score)
      }
    }
    val p = new PathGrowingWeightedMatching(g)
    val m = p.getMatching
    (m.getEdges.asScala.map { edge =>
      Pairing(g.getEdgeSource(edge), g.getEdgeTarget(edge))
    }.toList, nextBye)
  }
}

object CircleMethodPairing extends PairingStrategy {
  def pair(roundNumber: Int, scores: Vector[PlayerScore]): (List[Pairing], Option[String]) = {
    val players = scores.map(_.player).sorted.map(Some(_)) ++ (if (scores.length % 2 != 0) Some(None) else None)
    val playersToRotate = players.drop(1)
    val circle = players.take(1) ++ playersToRotate.takeRight(roundNumber % (players.length - 1)) ++ playersToRotate.dropRight(roundNumber % (players.length - 1))
    val pairings = circle.take(circle.length / 2).zip(circle.drop(circle.length / 2).reverse)
    val nextBye = pairings.collectFirst {
      case (Some(p), None) => p
      case (None, Some(p)) => p
    }
    (pairings.collect { case (Some(p1), Some(p2)) => Pairing(p1, p2) }.toList, nextBye)
  }
}

object PairingStrategy {
  implicit val pairingStrategyDecoder: Decoder[PairingStrategy] = deriveDecoder[PairingStrategy]
  implicit val pairingStrategyEncoder: Encoder[PairingStrategy] = deriveEncoder[PairingStrategy]

  def nextBye(scores: Vector[PlayerScore]): Option[String] =
    if (scores.length % 2 != 0) {
      val (nByes, maxByes) = {
        val byes = scores.map(s => s.player -> s.opponents.count(_.isEmpty)).toMap
        (byes, byes.maxBy(_._2)._2)
      }

      scores.reverseIterator.find(s => nByes(s.player) != maxByes).orElse(Some(scores.reverseIterator.next())).map(_.player)
    } else None
}
