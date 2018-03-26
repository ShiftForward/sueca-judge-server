package eu.shiftforward.suecajudge.http

import akka.http.scaladsl.model._
import StatusCodes._
import akka.http.scaladsl.server._
import Directives._
import org.apache.logging.log4j.scala.Logging

object CustomHandlers extends Logging {

  import Marshallers._

  implicit val rejectionHandler: RejectionHandler = RejectionHandler.newBuilder()
    .handleNotFound { complete(NotFound, html.error("Página não encontrada")) }
    .result()
    .withFallback(RejectionHandler.default)
    .mapRejectionResponse {
      case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
        res.copy(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, ent.data.utf8String))

      case x => x
    }

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case EntityStreamSizeException(_, _) =>
      complete(RequestEntityTooLarge, html.error("O tamanho do ficheiro ultrapassa o limite"))

    case ex =>
      extractUri { uri =>
        logger.error(s"Error in route $uri", ex)
        complete(InternalServerError, html.error("Aconteceu um erro interno"))
        // we probably don't want to leak the real exception
        // complete(InternalServerError, html.error("Erro", ex.toString))
      }

  }
}
