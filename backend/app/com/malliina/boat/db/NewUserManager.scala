package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.db.BoatDatabase.fail
import com.malliina.boat.db.NewUserManager.log
import com.malliina.boat.{BoatInfo, BoatNames, BoatToken, BoatTokens, JoinedBoat, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken}
import com.malliina.values.{Email, UserId, Username}
import io.getquill.SnakeCase
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatDatabase[SnakeCase]): NewUserManager =
    new NewUserManager(db)(db.ec)
}

class NewUserManager(val db: BoatDatabase[SnakeCase])(implicit ec: ExecutionContext)
    extends UserManager {
  import db._

  val userByEmail = quote { email: Email =>
    usersTable.filter(u => u.email.contains(email))
  }
  val userById = quote { id: UserId =>
    usersTable.filter(u => u.id == id)
  }

  val usersWithBoats = quote { q: Query[UserRow] =>
    q.leftJoin(boatsTable).on(_.id == _.owner).map {
      case (user, boatOpt) =>
        JoinedUser(user, boatOpt)
    }
  }
  val allUsers = quote(usersWithBoats(usersTable))
  val loadBoats = quote { q: Query[JoinedTrack] =>
    q.sortBy(t => (t.boat.username, t.boat.device, t.start, t.trackAdded, t.track))(
      Ord(Ord.ascNullsLast, Ord.ascNullsLast, Ord.descNullsLast, Ord.descNullsLast, Ord.desc)
    )
  }
  val loadDevices = quote { email: Email =>
    boatsView.filter(bv => bv.email.contains(email) && !tracksTable.map(_.boat).contains(bv.device))
  }

  def users: Future[Seq[UserInfo]] = Future {
    perform("All users", runIO(allUsers).map(DatabaseUserManager.collect))
  }

  override def userInfo(email: Email): Future[UserInfo] = execute {
    val task = for {
      userId <- getOrCreate(email)
      info <- runIO(usersWithBoats(userById(lift(userId))))
        .map(DatabaseUserManager.collect)
    } yield info.headOption.map { profile =>
      if (profile.enabled) Right(profile)
      else Left(UserDisabled(profile.username))
    }.getOrElse {
      Left(InvalidCredentials(None))
    }
    perform("Obtain user info", task)
  }

  def userMeta(email: Email): Future[UserRow] = Future {
    run(userByEmail(lift(email))).headOption.getOrElse(fail(s"User not found: '$email'."))
  }

  override def authBoat(token: BoatToken): Future[JoinedBoat] = execute {
    run(boatsView.filter(_.boatToken == lift(token))).headOption
      .map(Right.apply)
      .getOrElse(Left(InvalidToken(token)))
  }

  override def boats(email: Email): Future[UserBoats] = Future {
    val task = for {
      id <- getOrCreate(email)
      byId <- runIO(userById(lift(id))).map(_.headOption)
      user <- byId.map(IO.successful).getOrElse(IO.failed(fail(s"User not found: '$id'.")))
      boatRows <- runIO(
        loadBoats(nonEmptyTracks.filter(t => t.boat.userId == lift(id) && t.points > 100))
      )
      bs = DatabaseUserManager.collectBoats(boatRows, TimeFormatter(user.language))
      devices <- runIO(loadDevices(lift(email)))
    } yield {
      val gpsDevices = devices.map(d => BoatInfo(d.device, d.boatName, d.username, d.language, Nil))
      UserBoats(user.user, user.language, bs ++ gpsDevices)
    }
    perform("Load boats", task)
  }

  override def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]] = Future {
    val task = for {
      id <- runIO(
        usersTable
          .insert(
            _.user -> lift(user.username),
            _.email -> lift(user.email),
            _.token -> lift(user.token),
            _.enabled -> lift(user.enabled)
          )
          .returningGenerated(_.id)
      )
      rows <- runIO(userById(lift(id)))
      user <- rows.headOption
        .map(IO.successful)
        .getOrElse(IO.failed(fail(s"User not found: '$id'.")))
    } yield user
    try {
      Right(perform("Add user", task))
    } catch {
      case e: SQLException if e.getMessage contains "primary key violation" =>
        Left(AlreadyExists(user.username))
    }
  }

  override def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] = Future {
    val changed = run(usersTable.filter(_.user == lift(user)).delete)
    if (changed > 0) {
      log.info(s"Deleted user '$user'.")
      Right(())
    } else {
      Left(UserDoesNotExist(user))
    }
  }

  override def changeLanguage(user: UserId, to: Language): Future[Boolean] = Future {
    val changed = run(userById(lift(user)).update(_.language -> lift(to)))
    val wasChanged = changed > 0
    if (wasChanged) {
      log.info(s"Changed language of user ID '$user' to '$to'.")
    }
    wasChanged
  }

  private def getOrCreate(email: Email) = for {
    existing <- runIO(userByEmail(lift(email)))
    userId <- existing.headOption.map(u => IO.successful(u.id)).getOrElse(addUserWithBoat(email))
  } yield userId

  private def addUserWithBoat(email: Email) = for {
    userId <- runIO(
      usersTable
        .insert(
          _.user -> lift(Username(email.email)),
          _.email -> lift(Option(email)),
          _.token -> lift(UserToken.random()),
          _.enabled -> lift(true)
        )
        .returningGenerated(_.id)
    )
    _ <- runIO(
      boatsTable.insert(
        _.name -> lift(BoatNames.random()),
        _.token -> lift(BoatTokens.random()),
        _.owner -> lift(userId)
      )
    )
  } yield userId

  private def execute[T](code: => Either[IdentityError, T]): Future[T] =
    Future(code).flatMap(
      _.fold(
        err => Future.failed(IdentityException(err)),
        user => Future.successful(user)
      )
    )
}
