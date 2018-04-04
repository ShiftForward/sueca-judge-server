package eu.shiftforward.suecajudge.http

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Success, Try }

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.ContentDispositionTypes.attachment
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.model.{ EntityStreamSizeException, Uri }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions.{ oneOff, usingCookies }
import com.softwaremill.session.{ SessionConfig, SessionManager }
import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.scala.Logging
import org.joda.time.DateTime

import eu.shiftforward.suecajudge.http.CustomDirectives._
import eu.shiftforward.suecajudge.http.HttpService.Session
import eu.shiftforward.suecajudge.http.Validation.MaxPayloadSize
import eu.shiftforward.suecajudge.storage.{ DB, Player }

trait HttpService extends SubmissionsDirectives with Logging {

  import Marshallers._

  implicit val sessionManager: SessionManager[String] =
    new SessionManager[String](SessionConfig.fromConfig())

  val extractSessionContext: Directive1[Session] =
    optionalSession(oneOff, usingCookies).map(Session.apply)

  val sessionRejectionHandler = RejectionHandler.newBuilder()
    .handle { case AuthorizationFailedRejection => invalidateSession(oneOff, usingCookies)(redirect("/login", Found)) }
    .result()

  val requireSession: Directive1[String] = {
    handleRejections(sessionRejectionHandler).tflatMap { _ =>
      requiredSession(oneOff, usingCookies).flatMap { user =>
        onComplete(DB.players.isValidUser(user)).flatMap {
          case Success(true) => provide(user)
          case _ => reject(AuthorizationFailedRejection)
        }
      }
    }
  }

  val submissionsTimeLimit = {
    val str = ConfigFactory.load.getConfig("sueca-http-server").getString("submissions-time-limit")
    new DateTime(str)
  }

  val finalLeaderboard =
    ConfigFactory.load.getConfig("sueca-http-server").getBoolean("final-leaderboard")

  def shouldRedirectToHttps: Boolean

  // format: OFF
  private val healthCheckRoutes = (get & path("ping")) { complete(OK) }
  private val logicRoutes = (encodeResponse & redirectToHttps(shouldRedirectToHttps)) {
    extractSessionContext { implicit session =>

      pathEndOrSingleSlash {
        get {
          complete(html.index())
        }
      } ~
      path("register") {
        get {
          if (session.username.isDefined) redirect("/leaderboard", Found)
          else complete(html.register())
        } ~
        post {
          toStrictEntity(10.seconds) {
            formFields("username", "password", "entryCode", "name", "institution", "email") { (username, password, entryCode, name, institution, email) =>
              val player = Player(username, password, entryCode, name, institution, email, false, 0)

              onSuccess(DB.players.register(player)) {
                case Right(_) =>
                  setSession(oneOff, usingCookies, username) {
                    redirect("/leaderboard", Found)
                  }
                case Left(errors) =>
                  complete(BadRequest, html.register(Some(player), errors))
              }
            }
          }
        }
      } ~
      path("login") {
        get {
          if (session.username.isDefined) redirect("/leaderboard", Found)
          else complete(html.login())
        } ~
        post {
          toStrictEntity(10.seconds) {
            formFields("username", "password") { (username, password) =>
              onSuccess(DB.players.login(username, password)) {
                case Right(_) =>
                  setSession(oneOff, usingCookies, username) {
                    redirect("/leaderboard", Found)
                  }
                case Left(error) =>
                  complete(BadRequest, html.login(Some(error)))
              }
            }
          }
        }
      } ~
      path("users" / "logout") {
        (get | post) {
          invalidateSession(oneOff, usingCookies) {
            redirect("/", Found)
          }
        }
      } ~
      pathPrefix("users" / "submission") {
        pathEndOrSingleSlash {
          (get & requireSession) { username =>
            onSuccess(DB.players.latestSubmission(username)) {
              case Some(s) =>
                respondWithHeader(`Content-Disposition`(attachment, Map("attachment" -> "", "filename" -> s.filename))) {
                  Try(new String(s.payload)).toOption match {
                    case Some(textFile) => complete(textFile)
                    case None => complete(s.payload)
                  }
                }
              case None => complete(NoContent)
            }
          }
        } ~
        path(Segment) { username =>
          (get & validate(finalLeaderboard, "Disponível apenas após o concurso terminar")) {
            onSuccess(DB.players.latestSubmission(username)) {
              case Some(s) =>
                respondWithHeader(`Content-Disposition`(attachment, Map("attachment" -> "", "filename" -> s.filename))) {
                  Try(new String(s.payload)).toOption match {
                    case Some(textFile) => complete(textFile)
                    case None => complete(s.payload)
                  }
                }
              case None => complete(NoContent)
            }
          }
        }
      } ~
      path("rules") {
        get {
          complete(html.rules())
        }
      } ~
      path("submit") {
        get {
          requireSession { username =>
            onSuccess(DB.players.latestSubmission(username)) { s =>
              complete(html.submit(s, submissionLimitPast = DateTime.now().isAfter(submissionsTimeLimit)))
            }
          }
        } ~
        post {
          if (DateTime.now().isAfter(submissionsTimeLimit)) {
            redirect("/submit", Found)
          } else {
            requireSession { username =>
              handleExceptions(ExceptionHandler {
                case EntityStreamSizeException(_, _) =>
                  onSuccess(DB.players.latestSubmission(username)) { s =>
                    complete(BadRequest, html.submit(s, Map("payload" ->
                      List(s"Tamanho do ficheiro ultrapassa o limite (${MaxPayloadSize / 1000}kb)"))))
                  }
              }) {
                toStrictEntity(10.seconds) {
                  extractSubmission(username) { submission =>
                    extractExecutionContext { implicit ec =>
                      onSuccess(Validation.tryUserSubmission(submission)) {
                        case Right(_) => redirect("/submit", Found)
                        case Left(errors) =>
                          onSuccess(DB.players.latestSubmission(username)) { s =>
                            complete(BadRequest, html.submit(s, errors))
                          }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      } ~
      path("leaderboard") {
        get {
          onSuccess(DB.players.getLeaderboard().zip(session.username.map(DB.players.latestSubmission).getOrElse(Future.successful(None)))) {
            case ((leaderboard, computedAt), latestSubmission) =>
              complete(html.leaderboard(leaderboard, latestSubmission.map(_.state), computedAt, finalLeaderboard))
          }
        }
      } ~
      getFromResourceDirectory("webapp") ~
      pathEndOrSingleSlash { redirect(Uri("/"), Found) }
    }
  }
  val serviceRoutes = healthCheckRoutes ~ logicRoutes
  // format: ON
}

object HttpService {
  case class Session(username: Option[String])
}
