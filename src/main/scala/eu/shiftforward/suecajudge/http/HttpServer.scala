package eu.shiftforward.suecajudge.http

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.storage.SeedData

class HttpServer extends HttpService with Logging {

  lazy val config = ConfigFactory.load.getConfig("sueca-http-server")
  lazy val shouldRedirectToHttps = config.getBoolean("redirect-to-https")

  implicit val system: ActorSystem =
    ActorSystem("HttpServerSystem", config)

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  SeedData.load()

  import CustomHandlers._

  def run(): Unit = {
    Http(system).bindAndHandle(serviceRoutes, "0.0.0.0", 8090).map { bnd =>
      logger.info(s"Bound to ${bnd.localAddress}")
    }
  }
}
