package eu.shiftforward.suecajudge.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._

object CustomDirectives {

  def redirectToHttps(enabled: Boolean): Directive0 = {
    extractUri.flatMap { uri =>
      optionalHeaderValueByName("X-Forwarded-Proto").flatMap { originalProto =>
        if (enabled && originalProto.getOrElse(uri.scheme) != "https") {
          redirect(uri.copy(scheme = "https"), StatusCodes.Found)
        } else pass
      }
    }
  }
}
