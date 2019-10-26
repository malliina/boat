package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.db.NewUserManager.log
import com.malliina.boat.{BoatInfo, BoatNames, BoatToken, BoatTokens, JoinedBoat, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken, Usernames}
import com.malliina.values.{Email, UserId, Username}
import io.getquill.SnakeCase
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatDatabase[SnakeCase]): NewUserManager = new NewUserManager(db)
}

class NewUserManager(val db: BoatDatabase[SnakeCase]) extends UserManager {
  import db._
  implicit val exec: ExecutionContext = db.ec
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
  val userInsertion = quote { user: NewUser =>
    usersTable
      .insert(
        _.user -> user.user,
        _.email -> user.email,
        _.token -> user.token,
        _.enabled -> user.enabled
      )
      .returningGenerated(_.id)
  }

  def users: Future[Seq[UserInfo]] =
    performAsync("All users") { runIO(allUsers).map(DatabaseUserManager.collect) }

  override def userInfo(email: Email): Future[UserInfo] = transactionally("Load user info") {
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
    fold(task)
  }

  def userMeta(email: Email): Future[UserRow] = performAsync("Load user by email") {
    first(runIO(userByEmail(lift(email))), s"User not found: '$email'.")
  }

  override def authBoat(token: BoatToken): Future[JoinedBoat] = performAsync("Authenticate boat") {
    fold(
      runIO(boatsView.filter(_.boatToken == lift(token)))
        .map(_.headOption.map(b => Right(b)).getOrElse(Left(InvalidToken(token))))
    )
  }

  override def boats(email: Email): Future[UserBoats] = transactionally("Load boats") {
    for {
      id <- getOrCreate(email)
      user <- first(runIO(userById(lift(id))), s"User not found: '$id'.")
      boatRows <- runIO(
        loadBoats(nonEmptyTracks.filter(t => t.boat.userId == lift(id) && t.points > 100))
      )
      devices <- runIO(loadDevices(lift(email)))
    } yield {
      val bs = DatabaseUserManager.collectBoats(boatRows, TimeFormatter(user.language))
      val gpsDevices = devices.map(d => BoatInfo(d.device, d.boatName, d.username, d.language, Nil))
      UserBoats(user.user, user.language, bs ++ gpsDevices)
    }
  }

  def initUser(user: Username = Usernames.anon): Future[NewUser] =
    transactionally(s"Insert user $user") {
      val anon = NewUser(user, None, UserToken.random(), enabled = true)
      for {
        exists <- runIO(usersTable.filter(_.user == lift(user)).nonEmpty)
        ok <- if (exists) IO.successful(user) else runIO(userInsertion(lift(anon)))
      } yield anon
    }

  override def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]] =
    transactionally("Add user") {
      for {
        id <- runIO(userInsertion(lift(user)))
        user <- first(runIO(userById(lift(id))), s"User not found: '$id'.")
      } yield user
    }.map {
      Right.apply
    }.recover {
      case e: SQLException if e.getMessage contains "primary key violation" =>
        Left(AlreadyExists(user.user))
    }

  override def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] =
    performAsync("Delete user") {
      runIO(usersTable.filter(_.user == lift(user)).delete).map { changed =>
        if (changed > 0) {
          log.info(s"Deleted user '$user'.")
          Right(())
        } else {
          Left(UserDoesNotExist(user))
        }
      }
    }

  override def changeLanguage(user: UserId, to: Language): Future[Boolean] =
    performAsync("Change language") {
      runIO(userById(lift(user)).update(_.language -> lift(to))).map { changed =>
        val wasChanged = changed > 0
        if (wasChanged) {
          log.info(s"Changed language of user ID '$user' to '$to'.")
        }
        wasChanged
      }
    }

  private def getOrCreate(email: Email) = for {
    existing <- runIO(userByEmail(lift(email)))
    userId <- existing.headOption.map(u => IO.successful(u.id)).getOrElse(addUserWithBoat(email))
  } yield userId

  private def addUserWithBoat(email: Email) = for {
    userId <- runIO(userInsertion(lift(NewUser.email(email))))
    _ <- runIO(
      boatsTable.insert(
        _.name -> lift(BoatNames.random()),
        _.token -> lift(BoatTokens.random()),
        _.owner -> lift(userId)
      )
    )
  } yield userId

  private def fold[T, E <: Effect](io: IO[Either[IdentityError, T], E]): IO[T, E] = io.flatMap(
    _.fold(
      err => IO.failed(IdentityException(err)),
      user => IO.successful(user)
    )
  )
}
