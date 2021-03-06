package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.InviteState.accepted
import com.malliina.boat.db.DoobieMappings._
import com.malliina.boat.db.DoobieUserManager.log
import com.malliina.boat.http.InviteResult.{AlreadyInvited, Invited, UnknownEmail}
import com.malliina.boat.http.{AccessResult, InviteInfo, InviteResult}
import com.malliina.boat.{Boat, BoatInfo, BoatNames, BoatToken, BoatTokens, DeviceId, FriendInvite, Invite, InviteState, JoinedBoat, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken, Usernames}
import com.malliina.util.AppLogger
import com.malliina.values.{Email, UserId, Username}
import doobie._
import doobie.implicits._

object DoobieUserManager {
  private val log = AppLogger(getClass)

  def apply(db: DoobieDatabase): DoobieUserManager = new DoobieUserManager(db)

  def collectUsers(rows: Seq[JoinedUser]): Vector[UserInfo] =
    rows.foldLeft(Vector.empty[UserInfo]) {
      case (acc, ub) =>
        val user = ub.user
        val idx = acc.indexWhere(_.id == user.id)
        val newBoats = ub.boat.toSeq.map { b => Boat(b.id, b.name, b.token, b.added.toEpochMilli) }
        val newInvites = ub.invite.toList.map { row =>
          Invite(row.boat, row.state, row.added.toEpochMilli)
        }
        val newFriends = ub.friend.toList.map { f =>
          FriendInvite(f.boat, f.friend, f.state, f.added.toEpochMilli)
        }
        if (idx >= 0) {
          val old = acc(idx)
          val unseenBoats =
            if (old.boats.exists(b => newBoats.exists(nb => nb.id == b.id))) Nil
            else newBoats
          val unseenInvites =
            if (old.invites.exists(i => newInvites.exists(ni => ni.boat == i.boat))) Nil
            else newInvites
          val unseenFriends =
            if (
              old.friends
                .exists(f => newFriends.exists(fi => fi.friend == f.friend && fi.boat == f.boat))
            ) Nil
            else newFriends
          acc.updated(
            idx,
            old.copy(
              boats = old.boats ++ unseenBoats,
              invites = old.invites ++ unseenInvites,
              friends = old.friends ++ unseenFriends
            )
          )
        } else {
          user.email.fold(acc) { email =>
            acc :+ UserInfo(
              user.id,
              user.user,
              email,
              user.language,
              newBoats,
              user.enabled,
              user.added.toEpochMilli,
              newInvites,
              newFriends
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

class DoobieUserManager(db: DoobieDatabase) extends UserManager with DoobieSQL {
  object sql extends CommonSql
  import db.{run, logHandler}
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
  def userMeta(email: Email): IO[UserRow] = run {
    userByEmailIO(email)
  }

  def userInfo(email: Email): IO[UserInfo] = run {
    def by(id: UserId) =
      sql"""select u.id, 
                   u.user, 
                   u.email, 
                   u.token, 
                   u.language, 
                   u.enabled, 
                   u.added, 
                   b.id boatId,
                   b.name boatName, 
                   b.token boatToken, 
                   b.owner boatOwner, 
                   b.added boatAdded, 
                   ub.boat as ubBoat, 
                   ubb.name as ubbName, 
                   ub.state as ubState,
                   ub.added as ubAdded,
                   fubb.id as fubbBoat,
                   fubb.name as fubbName, 
                   fu.id as fuId, 
                   fu.email as fuEmail, 
                   fub.state as fubState, 
                   fub.added as fubAdded
            from users u
            left join boats b on b.owner = u.id
            left join users_boats ub on u.id = ub.user
            left join boats ubb on ub.boat = ubb.id
            left join users_boats fub on fub.boat = b.id
            left join boats fubb on fub.boat = fubb.id
            left join users fu on fub.user = fu.id
            where u.id = $id""".query[JoinedUser].to[List]
    val task = for {
      userId <- getOrCreate(email)
      info <- by(userId).map(DoobieUserManager.collectUsers)
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

  def authBoat(token: BoatToken): IO[JoinedBoat] = run {
    val boats = CommonSql.boats
    log.info(s"Auth boat...")
    sql"$boats and b.token = $token".query[JoinedBoat].option.flatMap { opt =>
      opt.map { b =>
        log.info(s"Got boat $b...")
        pure(b)
      }.getOrElse {
        fail(IdentityException(InvalidToken(token)))
      }
    }
  }

  def boats(email: Email) = run {
    def boatRowsIO(id: UserId) =
      sql"""${sql.nonEmptyTracks} and (b.uid = $id or b.id in (select ub.boat from users_boats ub where ub.user = $id and ub.state = $accepted)) and t.points > 10"""
        .query[JoinedTrack]
        .to[List]
    def deviceRowsIO(email: Email) =
      sql"""${sql.boats} and u.email = $email and b.id not in (select boat from tracks)"""
        .query[JoinedBoat]
        .to[List]
    for {
      id <- getOrCreate(email)
      user <- userById(id).query[UserRow].unique
      userTracks <- boatRowsIO(id)
      devices <- deviceRowsIO(email)
    } yield {
      val bs = DoobieUserManager.collectBoats(userTracks, TimeFormatter(user.language))
      val gpsDevices = devices.map(d => BoatInfo(d.device, d.boatName, d.username, d.language, Nil))
      UserBoats(user.user, user.language, bs ++ gpsDevices)
    }
  }

  def initUser(user: Username = Usernames.anon): IO[NewUser] = run {
    val anon = NewUser(user, None, UserToken.random(), enabled = true)
    for {
      exists <- userByName(user).query[UserRow].option
      ok <- exists.map(u => pure(u.user)).getOrElse(userInsertion(anon))
    } yield anon
  }

  def addUser(user: NewUser): IO[Either[AlreadyExists, UserRow]] = run {
    userInsertion(user).flatMap { uid =>
      log.info(s"User $uid...")
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

  def deleteUser(user: Username): IO[Either[UserDoesNotExist, Unit]] = run {
    sql"""delete from users where user = $user""".update.run.map { changed =>
      if (changed > 0) {
        log.info(s"Deleted user '$user'.")
        Right(())
      } else {
        Left(UserDoesNotExist(user))
      }
    }
  }

  def changeLanguage(user: UserId, to: Language): IO[Boolean] = run {
    sql"""update users set language = $to where id = $user""".update.run.map { changed =>
      val wasChanged = changed > 0
      if (wasChanged) {
        log.info(s"Changed language of user ID '$user' to '$to'.")
      }
      wasChanged
    }
  }

  def invite(i: InviteInfo): IO[InviteResult] = run {
    userByEmail(i.email).query[UserRow].option.flatMap { invitee =>
      invitee.map { user =>
        addInviteIO(i.boat, user.id, i.principal)
      }.getOrElse {
        pure(UnknownEmail(i.email): InviteResult)
      }
    }
  }

  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): IO[InviteResult] = run {
    addInviteIO(boat, to, principal)
  }

  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): IO[AccessResult] = run {
    manageGroups(boat, from, principal) { existed =>
      if (existed) {
        sql"delete from users_boats where boat = $boat and user = $from".update.run.map(changed =>
          if (changed == 1) AccessResult(existed)
          else AccessResult(false)
        )
      } else {
        pure(AccessResult(existed))
      }
    }
  }

  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): IO[Long] = run {
    sql"update users_boats set state = ${state.name} where boat = $boat and user = $user".update.run
      .map(_.toLong)
  }

  private def linkInvite(boat: DeviceId, to: UserId) =
    sql"""insert into users_boats(user, boat, state) values($to, $boat, ${InviteState.awaiting})"""

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

  private def userBoat(boat: DeviceId, user: UserId) =
    sql"""select user, boat, state, added
          from users_boats 
          where user = $user and boat = $boat"""

  private def addInviteIO(
    boat: DeviceId,
    to: UserId,
    principal: UserId
  ): ConnectionIO[InviteResult] =
    manageGroups(boat, to, principal) { existed =>
      if (existed) {
        pure(AlreadyInvited(to, boat))
      } else {
        linkInvite(boat, to).update.run.map { _ =>
          Invited(to, boat)
        }
      }
    }

  private def manageGroups[T](boat: DeviceId, from: UserId, principal: UserId)(
    run: Boolean => ConnectionIO[T]
  ): ConnectionIO[T] = {
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
        sql"select ub.user, u.email from users_boats ub, users u where ub.user = u.id and ub.boat = $boat and ub.user = $from"
          .query[UserId]
          .option
      res <- run(link.isDefined)
    } yield res
  }
}
