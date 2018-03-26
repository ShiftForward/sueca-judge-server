package eu.shiftforward.suecajudge.http

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import eu.shiftforward.suecajudge.http.SubmissionsDirectives.UserSubmission

trait SubmissionsDirectives {

  def extractSubmission(userId: String): Directive1[UserSubmission] = {
    extractExecutionContext.flatMap { implicit ec: ExecutionContext =>
      extractMaterializer.flatMap { implicit mat: Materializer =>
        (fileUpload("payload") & formFields("language")).tflatMap {
          case ((metadata, source), language) =>
            val sink = Sink.fold[ByteString, ByteString](ByteString(""))(_.concat(_))
            onSuccess(source.runWith(sink).map { payloadStr =>
              UserSubmission(userId, language, metadata.fileName, payloadStr.toArray)
            })
        }
      }
    }
  }
}

object SubmissionsDirectives {
  case class UserSubmission(user: String, language: String, filename: String, payload: Array[Byte])
}
