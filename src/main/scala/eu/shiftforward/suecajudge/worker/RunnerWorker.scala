package eu.shiftforward.suecajudge.worker

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.scala.Logging

class RunnerWorker extends Logging {
  def run(): Unit = {
    val system = ActorSystem("WorkerSystem", ConfigFactory.load.getConfig("sueca-runner-worker"))
  }
}
