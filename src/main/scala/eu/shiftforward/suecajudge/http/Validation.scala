package eu.shiftforward.suecajudge.http

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Try
import scala.util.matching.Regex

import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.http.SubmissionsDirectives.UserSubmission
import eu.shiftforward.suecajudge.safeexec.Language
import eu.shiftforward.suecajudge.storage.{ DB, Submission }

object Validation extends Logging {
  case class ValidationRule[T](field: String, check: T => Boolean, summary: String)

  def validateAndAccumulateErrors[T](obj: T, rules: List[ValidationRule[T]]): (Boolean, Map[String, List[String]]) = {
    val errors = rules.foldLeft(Map.empty[String, List[String]]) {
      case (acc, ValidationRule(field, check, summary)) =>
        if (check(obj)) {
          acc
        } else {
          val newError = acc.getOrElse(field, List.empty) :+ summary
          acc.updated(field, newError)
        }
    }

    (errors.isEmpty, errors)
  }

  private final val BackOffDuration = 30.seconds
  final val MaxPayloadSize = 51200

  private val validationRules: List[ValidationRule[UserSubmission]] = List(
    ValidationRule(
      "language",
      s => Try(Language(s.language)).isSuccess,
      "Linguagem inválida"),
    ValidationRule(
      "payload",
      s => s.payload.length < MaxPayloadSize,
      s"Tamanho do ficheiro ultrapassa o limite (${MaxPayloadSize / 1000}kb)"),
    ValidationRule(
      "payload",
      s => s.filename.nonEmpty && s.payload.nonEmpty,
      "Não pode estar vazio"))

  def timeRule(now: Long, lastSub: Long) =
    ValidationRule(
      "time",
      (_: UserSubmission) => (now - lastSub) > BackOffDuration.toMillis,
      s"Tempo entre submissões não pode ser inferior a ${BackOffDuration.toSeconds} segundos")

  def tryUserSubmission(userSubmission: UserSubmission)(implicit ec: ExecutionContext): Future[Either[Map[String, List[String]], Unit]] = {
    val now = System.currentTimeMillis()

    val fut = DB.players.latestSubmission(userSubmission.user).map(_.map(_.submittedAt)).map {
      case Some(timestamp) => validationRules :+ timeRule(now, timestamp)
      case None => validationRules
    }.flatMap { rules =>
      val (isValid, errors) = validateAndAccumulateErrors(userSubmission, rules)
      if (isValid) {
        val submission = Submission(
          user = userSubmission.user,
          language = Language(userSubmission.language),
          filename = userSubmission.filename,
          payload = userSubmission.payload)
        DB.players.submit(submission).map(_ => Right(()))
      } else {
        Future.successful(Left(errors))
      }
    }

    fut.onComplete { _ =>
      val diff = System.currentTimeMillis() - now
      logger.info(s"User submission was validated after $diff milliseconds.")
    }

    fut
  }

  def regexMatch[T](str: String, regex: Regex): Boolean = regex.pattern.matcher(str).matches
}
