package eu.shiftforward.suecajudge.worker

import akka.actor.{ ActorSystem, Props }
import akka.cluster.routing.{ ClusterRouterPool, ClusterRouterPoolSettings }
import akka.routing.RoundRobinPool
import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.scala.Logging

class RunnerMaster(local: Boolean) extends Logging {
  def run(): Unit = {
    val config = ConfigFactory.load.getConfig(if (local) "sueca-runner" else "sueca-runner-master")
    val system = ActorSystem("WorkerSystem", config)

    val runnersPerNode = config.getInt("runners-per-node")
    val runnerPool =
      if (local) {
        system.actorOf(RoundRobinPool(runnersPerNode).props(Props(new SubmissionRunnerActor)), name = "runnerPool")
      } else {
        system.actorOf(
          ClusterRouterPool(RoundRobinPool(0), ClusterRouterPoolSettings(
            totalInstances = 10000, maxInstancesPerNode = runnersPerNode, allowLocalRoutees = false))
            .props(Props[SubmissionRunnerActor]), name = "runnerPool")
      }

    system.actorOf(Props(new TournamentRunnerActor(runnerPool, config.getConfig("tournament"))))
    system.actorOf(Props(new ValidatorActor(runnerPool, config.getConfig("validator"))))
  }
}
