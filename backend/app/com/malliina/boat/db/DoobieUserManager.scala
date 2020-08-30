package com.malliina.boat.db

import com.malliina.boat.db.DoobieMappings._
import com.malliina.boat.http.{AccessResult, InviteInfo}
import com.malliina.boat.{BoatInfo, BoatNames, BoatToken, BoatTokens, DeviceId, InviteState, JoinedBoat, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken, Usernames}
import com.malliina.values.{Email, UserId, Username}
import doobie._
import doobie.implicits._
import play.api.Logger

import scala.concurrent.Future

class DoobieUserManager(db: DoobieDatabase) {
  object sql extends CommonSql
  private val log = Logger(getClass)

  val userColumns = fr"u.id, u.user, u.email, u.token, u.language, u.enabled, u.added"
  val selectUsers = sql"select $userColumns from users u"

  def userInsertion(user: NewUser) =
    sql"""insert into users(user, email, token, enabled)
          values(${user.user}, ${user.email}, ${user.token}, ${user.enabled})""".update
      .withUniqueGeneratedKeys[UserId]("id")
  def userById(id: UserId) = sql"$selectUsers where u.id = $id"
  def userByEmail(email: Email) = sql"$selectUsers where u.email = $email"
  def userByName(name: Username) = sql"$selectUsers where u.user = $name"
  def userByEmailIO(email: Email) = userByEmail(email).query[UserRow].unique
  def userMeta(email: Email): Future[UserRow] = db.run {
    userByEmailIO(email)
  }

  def userInfo(email: Email): Future[UserInfo] = db.run {
    def by(id: UserId) =
      sql"""select $userColumns, b.id boatId, b.name boatName, b.token boatToken, b.owner boatOwner, b.added boatAdded
            from users u
            left join boats b on b.owner = u.id
            where u.id = $id""".query[JoinedUser].to[List]
    val task = for {
      userId <- getOrCreate(email)
      info <- by(userId).map(NewUserManager.collect)
    } yield info
    task.flatMap[UserInfo] { infos =>
      infos.headOption.map { profile =>
        // Type annotation helps here for some reason
        val checked: ConnectionIO[UserInfo] =
          if (profile.enabled) pure(profile)
          else fail(IdentityException(UserDisabled(profile.username)))
        checked
      }.getOrElse {
        fail(IdentityException(InvalidCredentials(None)))
      }
    }
  }

  def authBoat(token: BoatToken): Future[JoinedBoat] = db.run {
    val boats = CommonSql.boats
    sql"$boats and b.token = $token".query[JoinedBoat].option.flatMap { opt =>
      opt.map { b => pure(b) }.getOrElse { fail(IdentityException(InvalidToken(token))) }
    }
  }

  def boats(email: Email) = db.run {
    def boatRowsIO(id: UserId) = sql"""${sql.nonEmptyTracks} and owner = $id and t.points > 100"""
      .query[JoinedTrack]
      .to[List]
    def deviceRowsIO(email: Email) =
      sql"""${sql.boats} and email = $email and id not in (select boat from tracks)"""
        .query[JoinedBoat]
        .to[List]
    for {
      id <- getOrCreate(email)
      user <- userById(id).query[UserRow].unique
      boatRows <- boatRowsIO(id)
      devices <- deviceRowsIO(email)
    } yield {
      val bs = NewUserManager.collectBoats(boatRows, TimeFormatter(user.language))
      val gpsDevices = devices.map(d => BoatInfo(d.device, d.boatName, d.username, d.language, Nil))
      UserBoats(user.user, user.language, bs ++ gpsDevices)
    }
  }

  def initUser(user: Username = Usernames.anon): Future[NewUser] = db.run {
    val anon = NewUser(user, None, UserToken.random(), enabled = true)
    for {
      exists <- userByName(user).query[UserRow].option
      ok <- exists.map(u => pure(u.user)).getOrElse(userInsertion(anon))
    } yield anon
  }

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]] = db.run {
    userInsertion(user).flatMap { uid =>
      userById(uid).query[UserRow].unique
    }.map[Either[AlreadyExists, UserRow]] { u =>
      Right(u)
    }.exceptSql { sqle =>
      if (sqle.getMessage.contains("primary key violation"))
        pure(Left(AlreadyExists(user.user)))
      else
        fail(sqle)
    }
  }

  def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] = db.run {
    sql"""delete from users where user = $user""".update.run.map { changed =>
      if (changed > 0) {
        log.info(s"Deleted user '$user'.")
        Right(())
      } else {
        Left(UserDoesNotExist(user))
      }
    }
  }

  def changeLanguage(user: UserId, to: Language): Future[Boolean] = db.run {
    sql"""update users set language = $to where id = $user""".update.run.map { changed =>
      val wasChanged = changed > 0
      if (wasChanged) {
        log.info(s"Changed language of user ID '$user' to '$to'.")
      }
      wasChanged
    }
  }

  def userBoat(boat: DeviceId, user: UserId) =
    sql"""select user, boat, state, added
         from users_boats 
         where user = $user and boat = $boat"""

  def invite(i: InviteInfo): Future[AccessResult] = db.run {
    userByEmail(i.email).query[UserRow].option.flatMap { invitee =>
      invitee.map { user =>
        addInviteIO(i.boat, user.id, i.principal)
      }.getOrElse {
        pure(AccessResult(false))
      }
    }
  }

  private def linkInvite(boat: DeviceId, to: UserId) =
    sql"""insert into users_boats(user, boat, state) values($to, $boat, ${InviteState.Awaiting.name})"""

  private def getOrCreate(email: Email) = for {
    existing <- userByEmail(email).query[UserRow].option
    userId <- existing.map(u => pure(u.id)).getOrElse(addUserWithBoat(email))
  } yield userId

  private def addUserWithBoat(email: Email) = {
    def boatInsertion(owner: UserId) =
      sql"""insert into boats(name, token, owner)
           values(${BoatNames.random()}, ${BoatTokens.random()}, $owner)""".update.run
    for {
      userId <- userInsertion(NewUser.email(email))
      _ <- boatInsertion(userId)
    } yield userId
  }
  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): Future[AccessResult] = db.run {
    addInviteIO(boat, to, principal)
  }

  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): Future[AccessResult] = db.run {
    manageGroups(boat, from, principal) { existed =>
      if (existed) sql"delete from users_boats where boat = $boat and user = $from".update.run
      else pure(0)
    }
  }

  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): Future[Long] = db.run {
    sql"update users_boats set state = ${state.name} where boat = $boat and user = $user".update.run
      .map(_.toLong)
  }

  private def addInviteIO(boat: DeviceId, to: UserId, principal: UserId) =
    manageGroups(boat, to, principal) { existed =>
      if (existed) {
        pure(0)
      } else {
        linkInvite(boat, to).update.run
      }
    }

  private def manageGroups[T](boat: DeviceId, from: UserId, principal: UserId)(
    run: Boolean => ConnectionIO[T]
  ): ConnectionIO[AccessResult] = {
    for {
      owns <-
        sql"""select b.id from boats b where b.id = $boat and b.owner = $principal"""
          .query[DeviceId]
          .option
      boatId <-
        owns
          .map(pure)
          .getOrElse(
            fail(
              s"User $principal is not authorized to modify access to boat $boat for user $from."
            )
          )
      link <-
        sql"select ub.user from users_boats ub where ub.boat = $boat and ub.user = $from"
          .query[UserId]
          .option
      res <- run(link.isDefined).map(t => AccessResult(link.isDefined))
    } yield res
  }

  protected def pure[A](a: A): ConnectionIO[A] = AsyncConnectionIO.pure(a)
  protected def fail[A](message: String): ConnectionIO[A] = fail(new Exception(message))
  protected def fail[A](e: Throwable): ConnectionIO[A] = AsyncConnectionIO.raiseError(e)

}
