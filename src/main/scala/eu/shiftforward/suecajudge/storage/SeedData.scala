package eu.shiftforward.suecajudge.storage

import java.nio.file.{ Files, Paths }
import java.sql.SQLException

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

import org.apache.logging.log4j.scala.Logging

object SeedData extends Logging {
  val entryCodesFile = Paths.get("entry_codes.txt")

  def load()(implicit ec: ExecutionContext): Future[Unit] = {
    if (Files.notExists(entryCodesFile)) Future.successful()
    else {
      logger.info(s"Loading entry codes from $entryCodesFile...")
      DB.players.addEntryCodes(Source.fromFile(entryCodesFile.toFile).getLines.toIterable).recover {
        case ex: SQLException => logger.warn(s"Exception while inserting seed data: ${ex.getMessage}")
      }
    }
  }
}
