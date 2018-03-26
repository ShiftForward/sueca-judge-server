package eu.shiftforward.suecajudge.storage

import eu.shiftforward.suecajudge.safeexec.Language
import eu.shiftforward.suecajudge.safeexec.Runner.ErrorCode
import eu.shiftforward.suecajudge.storage.Submission.{ SubmissionState, Submitted }
import io.circe._
import io.circe.generic.semiauto._

case class Submission(
    id: Option[Long] = None,
    user: String,
    language: Language,
    filename: String,
    payload: Array[Byte],
    compilationResult: Option[Array[Byte]] = None,
    state: SubmissionState = Submitted,
    submittedAt: Long = System.currentTimeMillis())

object Submission {
  sealed trait SubmissionState
  case object Submitted extends SubmissionState
  case object PreTested extends SubmissionState
  case object EvaluatedInTournament extends SubmissionState
  case class Error(errorCode: ErrorCode) extends SubmissionState

  object SubmissionState {
    implicit val stateEncoder: Encoder[SubmissionState] = deriveEncoder[SubmissionState]
    implicit val stateDecoder: Decoder[SubmissionState] = deriveDecoder[SubmissionState]
  }
}
