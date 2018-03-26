package eu.shiftforward.suecajudge.storage

import java.security.MessageDigest
import java.util.Base64

import scala.concurrent.{ ExecutionContext, Future }

import io.circe.parser.decode
import io.circe.syntax._
import org.apache.logging.log4j.scala.Logging
import slick.jdbc.JdbcProfile

import eu.shiftforward.suecajudge.game.{ Game, Match, PartialGame }
import eu.shiftforward.suecajudge.http.Validation._
import eu.shiftforward.suecajudge.safeexec.Language
import eu.shiftforward.suecajudge.storage.Submission._
import eu.shiftforward.suecajudge.tournament.{ PlayerScore, Result, SwissTournament }

case class Player(
    username: String,
    password: String,
    entryCode: String,
    name: String,
    institution: String,
    email: String,
    isBanned: Boolean,
    registeredAt: Long)

case class Score(
    rank: Int,
    user: String,
    score: Int,
    tournamentId: Long,
    wins: Int,
    draws: Int,
    losses: Int,
    aro: Double,
    elo: Int)

case class MatchEntry(
    id: Option[Long] = None,
    tournament: Long,
    submission1: Long,
    submission2: Long,
    data: Match,
    finishedAt: Long)

case class Tournament(
    id: Option[Long] = None,
    startedAt: Long,
    finishedAt: Option[Long] = None,
    data: Option[SwissTournament] = None)

trait PlayersAPI {
  def addEntryCodes(entryCodes: Iterable[String]): Future[Unit]
  def register(player: Player): Future[Either[Map[String, List[String]], Unit]]
  def login(username: String, password: String): Future[Either[String, Player]]
  def isValidUser(username: String): Future[Boolean]

  def submit(submission: Submission): Future[Submission]
  def latestSubmission(username: String): Future[Option[Submission]]
  def latestPendingSubmissions(): Future[Seq[Submission]]
  def latestSubmissions(): Future[Seq[Submission]]
  def setSubmissionState(id: Long, newState: SubmissionState): Future[Unit]
  def setSubmissionsState(ids: Iterable[Long], newState: SubmissionState): Future[Unit]
  def setSubmissionCompilationResult(id: Long, result: Array[Byte]): Future[Unit]

  def addMatchEntry(tournament: Long, sub1: Submission, sub2: Submission, data: Match, finishedAt: Long = System.currentTimeMillis()): Future[Unit]
  def startTournament(): Future[Tournament]
  def finishTournament(tournamentId: Long, finishedAt: Long, swissTournament: SwissTournament): Future[Unit]

  def getLeaderboard(): Future[(Seq[(Player, Score)], Long)]
  def updateLeaderboard(scores: List[PlayerScore], computedAt: Long): Future[Unit]
}

case class PlayersDB(profile: JdbcProfile) extends Logging {
  import profile.api._

  implicit val languageMapper: BaseColumnType[Language] = {
    MappedColumnType.base[Language, String](
      langObj => langObj.name,
      langStr => Language(langStr))
  }

  implicit val submissionMapper: BaseColumnType[SubmissionState] = {
    MappedColumnType.base[SubmissionState, String](
      stateObj => stateObj.asJson.noSpaces,
      stateStr => decode[SubmissionState](stateStr)
        .getOrElse(throw new RuntimeException("Error decoding submission state!")))
  }

  implicit val matchMapper: BaseColumnType[Match] = {
    MappedColumnType.base[Match, String](
      matchObj => matchObj.asJson.noSpaces,
      matchStr => decode[Match](matchStr)
        .getOrElse(throw new RuntimeException("Error decoding game.")))
  }

  implicit val swissTournamentMapper: BaseColumnType[SwissTournament] = {
    MappedColumnType.base[SwissTournament, String](
      tournamentObj => tournamentObj.asJson.noSpaces,
      tournamentStr => decode[SwissTournament](tournamentStr)
        .getOrElse(throw new RuntimeException("Error decoding swiss tournament.")))
  }

  class Players(tag: Tag) extends Table[Player](tag, "Players") {
    def username = column[String]("username", O.PrimaryKey)
    def password = column[String]("password")
    def entryCode = column[String]("entryCode", O.Unique)
    def name = column[String]("name")
    def institution = column[String]("institution")
    def email = column[String]("email", O.Unique)
    def isBanned = column[Boolean]("isBanned")
    def registeredAt = column[Long]("registeredAt")

    def * = (username, password, entryCode, name, institution, email, isBanned, registeredAt) <> (Player.tupled, Player.unapply)

    def entryCodeFk = foreignKey("entryCode_fk", entryCode, entryCodes)(
      _.entryCode, onUpdate = ForeignKeyAction.Restrict)
  }

  class EntryCodes(tag: Tag) extends Table[String](tag, "EntryCodes") {
    def entryCode = column[String]("entryCode", O.PrimaryKey)
    def * = entryCode
  }

  class Submissions(tag: Tag) extends Table[Submission](tag, "Submissions") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def user = column[String]("user")
    def language = column[Language]("language")
    def filename = column[String]("filename")
    def payload = column[Array[Byte]]("payload")
    def compilationResult = column[Option[Array[Byte]]]("compilation_result")
    def state = column[SubmissionState]("state")
    def submittedAt = column[Long]("submitted")

    def idxTimestamp = index("submissions_timestamp_idx", submittedAt)
    def idxUser = index("submissions_user_idx", user)
    def idxState = index("submissions_state_idx", state)
    def userFk = foreignKey("submissions_user_fk", user, players)(_.username, onUpdate = ForeignKeyAction.Restrict)

    def * = (id.?, user, language, filename, payload, compilationResult, state, submittedAt) <>
      ((Submission.apply _).tupled, Submission.unapply)
  }

  class Leaderboard(tag: Tag) extends Table[Score](tag, "Leaderboard") {
    def user = column[String]("user")
    def score = column[Int]("score")
    def tournament = column[Long]("tournament")
    def wins = column[Int]("wins")
    def draws = column[Int]("draws")
    def losses = column[Int]("losses")
    def aro = column[Double]("aro")
    def rank = column[Int]("rank")
    def elo = column[Int]("elo")

    def pk = primaryKey("user_computedAt_pk", (user, tournament))
    def tournamentFk = foreignKey("tournament_fk", tournament, tournaments)(_.id, onUpdate = ForeignKeyAction.Restrict)
    def userFk = foreignKey("user_score_fk", user, players)(_.username, onUpdate = ForeignKeyAction.Restrict)

    def * = (rank, user, score, tournament, wins, draws, losses, aro, elo) <> (Score.tupled, Score.unapply)
  }

  class Tournaments(tag: Tag) extends Table[Tournament](tag, "Tournaments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def startedAt = column[Long]("startedAt")
    def finishedAt = column[Option[Long]]("finishedAt")
    def data = column[Option[SwissTournament]]("data")

    def * = (id.?, startedAt, finishedAt, data) <> ((Tournament.apply _).tupled, Tournament.unapply)
  }

  class Matches(tag: Tag) extends Table[MatchEntry](tag, "Matches") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def tournament = column[Long]("tournament")
    def submission1 = column[Long]("submission1")
    def submission2 = column[Long]("submission2")
    def data = column[Match]("data")
    def finishedAt = column[Long]("finishedAt")

    def tournamentFk = foreignKey("tournament_fk", tournament, tournaments)(_.id, onUpdate = ForeignKeyAction.Restrict)
    def submission1Fk = foreignKey("submission1_fk", submission1, submissions)(_.id, onUpdate = ForeignKeyAction.Restrict)
    def submission2Fk = foreignKey("submission2_fk", submission2, submissions)(_.id, onUpdate = ForeignKeyAction.Restrict)

    def * = (id.?, tournament, submission1, submission2, data, finishedAt) <> ((MatchEntry.apply _).tupled, MatchEntry.unapply)
  }

  private[this] val sha256 = MessageDigest.getInstance("SHA-256")

  def hashPassword(password: String): String =
    Base64.getEncoder.encodeToString(sha256.digest(password.getBytes))

  val players = TableQuery[Players]
  val entryCodes = TableQuery[EntryCodes]
  val submissions = TableQuery[Submissions]
  val leaderboard = TableQuery[Leaderboard]
  val tournaments = TableQuery[Tournaments]
  val matches = TableQuery[Matches]

  def apply(db: Database)(implicit ec: ExecutionContext): PlayersAPI = new PlayersAPI {

    lazy val init: Future[Unit] = {
      val schemas = List(entryCodes.schema, players.schema, tournaments.schema, submissions.schema, leaderboard.schema, matches.schema)
      schemas.foldLeft(Future.successful(())) {
        case (f, s) =>
          f.flatMap(_ => db.run(s.create).recover { case ex => logger.warn(s"Exception while creating schema: ${ex.getMessage}") })
      }
    }

    def addEntryCodes(codes: Iterable[String]) = init.flatMap { _ =>
      db.run(entryCodes ++= codes).map(_ => ())
    }

    private val usernameRegex = "^[A-Za-z0-9]+(?:[_-][A-Za-z0-9]+)*$".r
    private val institutionRegex = "^[A-Za-z]{0,8}$".r
    private val emailRegex = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])".r

    def register(player: Player): Future[Either[Map[String, List[String]], Unit]] = init.flatMap { _ =>

      val conflictingPlayers = db.run(players
        .filter { p => p.username === player.username || p.email === player.email || p.entryCode === player.entryCode }
        .result.headOption)

      val validEntryCode = db.run(entryCodes
        .filter(_.entryCode === player.entryCode)
        .result.headOption)

      conflictingPlayers.zip(validEntryCode).flatMap {

        case (dbPlayer, entryCode) =>

          // format: OFF
          val playerValidationRules: List[ValidationRule[Player]] = List(
            ValidationRule("username",    p => regexMatch(p.username, usernameRegex)
              && p.username.length < 50 && p.username.length > 2,                           "Formato inválido"),
            ValidationRule("username",    p => dbPlayer.forall(_.username != p.username),   "Já utilizado"),
            ValidationRule("password",    _.password.nonEmpty,                              "Não pode estar vazio"),
            ValidationRule("entryCode",   p => dbPlayer.forall(_.entryCode != p.entryCode), "Já utilizado"),
            ValidationRule("entryCode",   p => entryCode.contains(p.entryCode),             "Código inválido"),
            ValidationRule("name",        _.name.nonEmpty,                                  "Não pode estar vazio"),
            ValidationRule("institution", p => regexMatch(p.institution, institutionRegex), "Usa a sigla da tua instituição"),
            ValidationRule("email",       p => dbPlayer.forall(_.email != p.email),         "Já utilizado"),
            ValidationRule("email",       p => regexMatch(p.email, emailRegex),             "Formato inválido"))
          // format: ON

          val (isValid, errors) = validateAndAccumulateErrors(player, playerValidationRules)
          if (isValid) {
            val dbPlayer = player.copy(
              password = hashPassword(player.password),
              institution = player.institution.toUpperCase,
              registeredAt = System.currentTimeMillis())

            db.run(players += dbPlayer).map(_ => Right(()))
          } else {
            Future.successful(Left(errors))
          }
      }
    }

    def login(username: String, password: String) = init.flatMap { _ =>
      db.run(players.filter(_.username === username).result.headOption).map {
        case None => Left("Dados incorretos")
        case Some(p) if p.password != hashPassword(password) => Left("Dados incorretos")
        case Some(p) if p.isBanned => Left("Utilizador desactivado")
        case Some(p) => Right(p)
      }
    }

    def isValidUser(username: String) =
      db.run(players.filter { u => u.username === username && !u.isBanned }.exists.result)

    def submit(submission: Submission) = init.flatMap { _ =>
      db.run((submissions returning submissions.map(_.id) into ((sub, id) => sub.copy(id = Some(id)))) += submission)
    }

    def addMatchEntry(
      tournamentId: Long,
      sub1: Submission,
      sub2: Submission,
      data: Match,
      finishedAt: Long) = init.flatMap { _ =>
      db.run(matches += MatchEntry(
        tournament = tournamentId, submission1 = sub1.id.get, submission2 = sub2.id.get, data = data, finishedAt = finishedAt)).map(_ => ())
    }

    def latestSubmission(username: String) = {
      init.flatMap { _ =>
        db.run(submissions
          .filter(s => s.user === username)
          .sortBy(_.submittedAt.desc)
          .take(1).result.headOption)
      }
    }

    def latestPendingSubmissions() = init.flatMap { _ =>
      db.run(submissions
        .filter { s =>
          // latest per user
          (s.submittedAt === submissions.filter(_.user === s.user).map(_.submittedAt).max) &&
            // not pre-tested
            s.state === (Submitted: SubmissionState)
        }
        .result)
    }

    def latestSubmissions() = init.flatMap { _ =>
      // It is not easy to use the Slick DSL with a type like Error, so we're doing the SQL query explicitly here.
      val validPlayers: Future[Set[String]] = db.run(
        sql"""|WITH Errors AS (SELECT
              |                  s.user,
              |                  MAX(submitted) AS ts
              |                FROM "Submissions" s
              |                WHERE s.state LIKE '%Error%'
              |                GROUP BY s.user),
              |    Valids AS (SELECT
              |                 s.user,
              |                 MAX(submitted) AS ts
              |               FROM "Submissions" s
              |               WHERE s.state LIKE '%PreTested%' OR s.state LIKE '%EvaluatedInTournament%'
              |               GROUP BY s.user),
              |    Users AS (SELECT DISTINCT (s.user) AS user
              |              FROM "Submissions" s)
              |SELECT u.user
              |FROM Users u
              |WHERE ((u.user NOT IN (SELECT "user"
              |                       FROM Errors)) AND u.user IN (SELECT "user"
              |                                                    FROM Valids)) OR
              |      ((SELECT MAX(ts)
              |        FROM Errors
              |        WHERE "user" = u.user) < (SELECT MAX(ts)
              |                                  FROM Valids
              |                                  WHERE "user" = u.user))""".stripMargin.as[String]).map(_.toSet)

      validPlayers.flatMap { v =>
        db.run(submissions
          .filter { s =>
            // latest pre-tested per user
            ((s.user inSet v) && s.submittedAt === submissions.filter(s1 => s1.user === s.user &&
              (s.state === (PreTested: SubmissionState) || s.state === (EvaluatedInTournament: SubmissionState))).map(_.submittedAt).max) &&
              // either pre-tested or already ran in tournament
              (s.state === (PreTested: SubmissionState) || s.state === (EvaluatedInTournament: SubmissionState))
          }
          .result)
      }
    }

    private[this] def validTransition(oldState: SubmissionState, newState: SubmissionState) = (oldState, newState) match {
      case (Submitted, PreTested) => true
      case (PreTested, EvaluatedInTournament) => true
      case (old, _: Error) if !old.isInstanceOf[Error] => true
      case _ => false
    }

    def setSubmissionState(id: Long, newState: SubmissionState) = init.flatMap { _ =>
      val submissionFilter = submissions.filter(_.id === id)
      db.run {
        submissionFilter.map(_.state).result.headOption
          .flatMap {
            case Some(oldState) if oldState == newState =>
              DBIO.successful(())
            case Some(oldState) if validTransition(oldState, newState) =>
              submissionFilter.map(_.state).update(newState)
            case Some(oldState) =>
              DBIO.failed(new RuntimeException(s"Illegal state update from state '$oldState' to '$newState'!"))
            case _ =>
              DBIO.failed(new RuntimeException(s"Submission $id not found!"))
          }.transactionally
      }.map(_ => ())
    }

    def setSubmissionsState(ids: Iterable[Long], newState: SubmissionState) = init.flatMap { _ =>
      val submissionFilter = submissions.filter(_.id.inSet(ids))
      db.run {
        submissionFilter.map { s => (s.id, s.state) }.result.flatMap { states =>
          val statesMap = states.toMap
          val idsToUpdate = ids.map { id => (id, statesMap.get(id)) }.filter {
            case (_, Some(oldState)) if oldState == newState => false
            case (_, Some(oldState)) if validTransition(oldState, newState) => true
            case (_, Some(oldState)) =>
              throw new RuntimeException(s"Illegal state update from state '$oldState' to '$newState'!")
            case (id, None) =>
              throw new RuntimeException(s"Submission $id not found!")
          }.map(_._1)

          submissions.filter(_.id.inSet(idsToUpdate)).map(_.state).update(newState)
        }.transactionally
      }.map(_ => ())
    }

    def setSubmissionCompilationResult(id: Long, result: Array[Byte]) = {
      require(result.nonEmpty, "Requires a non-empty array of bytes!")
      val submissionFilter = submissions.filter(_.id === id)

      db.run {
        submissionFilter.map(_.compilationResult).result.headOption.flatMap {
          case Some(None) =>
            submissionFilter.map(_.compilationResult).update(Some(result))
          case Some(Some(_)) =>
            DBIO.failed(new RuntimeException(s"Tried to save compilation result more than once!"))
          case None =>
            DBIO.failed(new RuntimeException(s"Submission $id not found!"))
        }.transactionally
      }.map(_ => ())
    }

    def startTournament(): Future[Tournament] = init.flatMap { _ =>
      db.run((tournaments returning tournaments.map(_.id) into ((t, id) => t.copy(id = Some(id)))) += Tournament(startedAt = System.currentTimeMillis()))
    }

    def finishTournament(tournamentId: Long, finishedAt: Long, swissTournament: SwissTournament) = init.flatMap { _ =>
      db.run(tournaments.filter(_.id === tournamentId)
        .map(t => (t.finishedAt, t.data)).update(Some(finishedAt), Some(swissTournament))).map(_ => ())
    }

    def getLeaderboard(): Future[(Seq[(Player, Score)], Long)] = init.flatMap { _ =>
      val query = for {
        t <- tournaments.filter(_.finishedAt === tournaments.map(_.finishedAt).max)
        s <- leaderboard.filter(_.tournament === t.id)
        p <- players if s.user === p.username
      } yield (t, p, s)

      // sort by score desc
      db.run(query.sortBy(_._3.rank).result)
        .map { scores =>
          (scores.map(v => v._2 -> v._3),
            if (scores.nonEmpty) scores.head._1.finishedAt.getOrElse(0l) else 0l)
        }
    }

    def updateLeaderboard(scores: List[PlayerScore], tournamentId: Long): Future[Unit] = init.flatMap { _ =>
      val sMap = scores.map(s => s.player -> s).toMap
      val dbScores = scores.zipWithIndex.map {
        case (s, i) =>
          Score(i + 1, s.player, s.score, tournamentId, s.wins, s.draws, s.losses, s.aro(s.opponents.flatMap(_.flatMap(sMap.get)).toList), s.elo)
      }

      db.run(leaderboard ++= dbScores).map(_ => ())
    }
  }
}
