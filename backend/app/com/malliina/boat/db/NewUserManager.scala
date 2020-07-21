package com.malliina.boat.db

import java.sql.SQLException

import com.malliina.boat.InviteState.Awaiting
import com.malliina.boat.db.NewUserManager.log
import com.malliina.boat.http.{AccessResult, BoatInvite, InviteInfo}
import com.malliina.boat.{Boat, BoatInfo, BoatNames, BoatToken, BoatTokens, DeviceId, InviteState, JoinedBoat, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken, Usernames}
import com.malliina.values.{Email, UserId, Username}
import io.getquill.SnakeCase
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object NewUserManager {
  private val log = Logger(getClass)

  def apply(db: BoatDatabase[SnakeCase]): NewUserManager = new NewUserManager(db)

  def collect(rows: Seq[JoinedUser]) = collectUsers(rows.map(r => (r.user, r.boat)))

  def collectUsers(rows: Seq[(UserRow, Option[BoatRow])]): Vector[UserInfo] =
    rows.foldLeft(Vector.empty[UserInfo]) {
      case (acc, (user, boat)) =>
        val idx = acc.indexWhere(_.id == user.id)
        val newBoats =
          boat.toSeq.map(b => Boat(b.id, b.name, b.token, b.added.toEpochMilli))
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(boats = old.boats ++ newBoats))
        } else {
          user.email.fold(acc) { email =>
            acc :+ UserInfo(
              user.id,
              user.user,
              email,
              user.language,
              newBoats,
              user.enabled,
              user.added.toEpochMilli
            )
          }
        }
    }

  def collectBoats(rows: Seq[JoinedTrack], formatter: TimeFormatter): Seq[BoatInfo] =
    rows.foldLeft(Vector.empty[BoatInfo]) { (acc, row) =>
      val boatIdx =
        acc.indexWhere(b => b.user == row.username && b.boatId == row.boatId)
      val newRow = row.strip(formatter)
      if (boatIdx >= 0) {
        val old = acc(boatIdx)
        acc.updated(boatIdx, old.copy(tracks = old.tracks :+ newRow))
      } else {
        acc :+ BoatInfo(
          row.boatId,
          row.boatName,
          row.username,
          row.language,
          Seq(newRow)
        )
      }
    }
}

class NewUserManager(val db: BoatDatabase[SnakeCase]) extends UserManager {
  import db._
  implicit val exec: ExecutionContext = db.ec
  val userByEmail = quote { email: Email => usersTable.filter(u => u.email.contains(email)) }
  val userById = quote { id: UserId => usersTable.filter(u => u.id == id) }
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
  val userBoat = quote { (boat: DeviceId, user: UserId) =>
    usersBoatsTable.filter { ub => ub.boat == boat && ub.user == user }
  }

  def users: Future[Seq[UserInfo]] =
    performAsync("All users") { runIO(allUsers).map(NewUserManager.collect) }

  override def userInfo(email: Email): Future[UserInfo] = transactionally("Load user info") {
    val task = for {
      userId <- getOrCreate(email)
      info <- runIO(usersWithBoats(userById(lift(userId))))
        .map(NewUserManager.collect)
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
      val bs = NewUserManager.collectBoats(boatRows, TimeFormatter(user.language))
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

  def invite(i: InviteInfo): Future[AccessResult] =
    transactionally(s"Invite ${i.email} to boat ${i.boat} by ${i.principal}.") {
      for {
        invitee <- runIO(userByEmail(lift(i.email)))
        result <- invitee.headOption.map { user =>
          manageGroupsAction(i.boat, user.id, i.principal)
        }.getOrElse {
          IO.successful(AccessResult(false))
        }
      } yield result
    }

  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): Future[AccessResult] =
    transactionally(s"Allow user $to access to $boat.") {
      manageGroupsAction(boat, to, principal)
    }

  private def manageGroupsAction(boat: DeviceId, to: UserId, principal: UserId) =
    manageGroups(boat, to, principal) { existed =>
      if (existed) {
        IO.successful(())
      } else {
        runIO(
          usersBoatsTable
            .insert(
              _.boat -> lift(boat),
              _.user -> lift(to),
              _.state -> lift(Awaiting: InviteState)
            )
        )
      }
    }

  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): Future[AccessResult] =
    performAsync(s"Revoke access for $from to $boat.") {
      manageGroups(boat, from, principal) { existed =>
        if (existed) runIO(userBoat(lift(boat), lift(from)).delete)
        else IO.successful(())
      }
    }

  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): Future[Long] =
    performAsync(s"Update invite to boat $boat for user $user to $state.") {
      runIO(userBoat(lift(boat), lift(user)).update(_.state -> lift(state)))
    }

  private def manageGroups[T](boat: DeviceId, from: UserId, principal: UserId)(
    run: Boolean => IO[T, Effect.Write]
  ) =
    for {
      owns <- runIO(
        boatsTable.filter(b => b.id == lift(boat) && b.owner == lift(principal)).nonEmpty
      )
      auth <-
        if (owns) {
          IO.successful(())
        } else {
          IO.failed(
            fail(
              s"User $principal is not authorized to modify access to boat $boat for user $from."
            )
          )
        }
      existed <- runIO(userBoat(lift(boat), lift(from)).nonEmpty)
      _ <- run(existed)
    } yield AccessResult(existed)

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
