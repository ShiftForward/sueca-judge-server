package eu.shiftforward.suecajudge.storage

import scala.concurrent.ExecutionContext.Implicits._

import com.typesafe.config.ConfigFactory
import slick.jdbc.{ H2Profile, JdbcProfile, PostgresProfile }

object DB {
  val profile: JdbcProfile = ConfigFactory.load.getString("slick.driver") match {
    case "org.h2.Driver" => H2Profile
    case "org.postgresql.Driver" => PostgresProfile
    case _ => throw new IllegalArgumentException("Unsupported database driver")
  }

  import profile.api._

  val db = Database.forConfig("slick")

  val players: PlayersAPI = PlayersDB(profile)(db)
}
