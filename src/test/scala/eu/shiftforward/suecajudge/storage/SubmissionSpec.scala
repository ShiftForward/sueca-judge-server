package eu.shiftforward.suecajudge.storage

import io.circe.parser.decode
import io.circe.syntax._
import org.specs2.mutable.Specification

import eu.shiftforward.suecajudge.safeexec.Runner.{ MalformedOutput, MemoryLimitExceeded }
import eu.shiftforward.suecajudge.storage.Submission._

class SubmissionSpec extends Specification {

  "A SubmissionState" should {
    "correctly serialize/deserialize to/from JSON" in {
      val submitted: SubmissionState = Submitted
      decode[SubmissionState](submitted.asJson.noSpaces) must beRight(submitted)

      val preTested: SubmissionState = PreTested
      decode[SubmissionState](preTested.asJson.noSpaces) must beRight(preTested)

      val evaluatedInTournament: SubmissionState = EvaluatedInTournament
      decode[SubmissionState](evaluatedInTournament.asJson.noSpaces) must beRight(evaluatedInTournament)

      val error1: SubmissionState = Error(MemoryLimitExceeded)
      decode[SubmissionState](error1.asJson.noSpaces) must beRight(error1)

      val error2: SubmissionState = Error(MalformedOutput("ABC"))
      decode[SubmissionState](error2.asJson.noSpaces) must beRight(error2)
    }
  }
}
