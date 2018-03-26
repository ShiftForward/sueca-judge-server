package eu.shiftforward.suecajudge.http

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.MediaTypes
import play.twirl.api.Html

object Marshallers {
  implicit val twirlMarshaller: ToEntityMarshaller[Html] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`text/html`)(_.toString)
}
