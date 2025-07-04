package com.malliina.boat.db

import com.malliina.boat.InviteState.accepted
import com.malliina.boat.db.DoobieUserManager.{collectBoats, log}
import com.malliina.boat.http.InviteResult.{AlreadyInvited, Invited, UnknownEmail}
import com.malliina.boat.http.{AccessResult, InviteInfo, InviteResult, LimitLike}
import com.malliina.boat.{BoatInfo, BoatNames, BoatToken, BoatTokens, DeviceId, FriendInvite, Invite, InviteState, JoinedSource, JoinedTrack, Language, TimeFormatter, UserBoats, UserInfo, UserToken, Usernames}
import com.malliina.database.DoobieDatabase
import com.malliina.util.AppLogger
import com.malliina.values.{Email, RefreshToken, UserId, Username}
import doobie.*
import doobie.implicits.*

object DoobieUserManager:
  private val log = AppLogger(getClass)

  private def collectUsers(rows: Seq[JoinedUser]): Vector[UserInfo] =
    rows.foldLeft(Vector.empty[UserInfo]): (acc, ub) =>
      val user = ub.user
      val idx = acc.indexWhere(_.id == user.id)
      val newBoats = ub.boat.toSeq.map: b =>
        b.toBoat
      val newInvites = ub.invite.toList.map: row =>
        Invite(row.boat, row.state, row.added.toEpochMilli)
      val newFriends = ub.friend.toList.map: f =>
        FriendInvite(f.boat, f.friend, f.state, f.added.toEpochMilli)
      if idx >= 0 then
        val old = acc(idx)
        val unseenBoats =
          if old.boats.exists(b => newBoats.exists(nb => nb.id == b.id)) then Nil
          else newBoats
        val unseenInvites =
          if old.invites.exists(i => newInvites.exists(ni => ni.boat == i.boat)) then Nil
          else newInvites
        val unseenFriends =
          if old.friends
              .exists(f => newFriends.exists(fi => fi.friend == f.friend && fi.boat == f.boat))
          then Nil
          else newFriends
        acc.updated(
          idx,
          old.copy(
            boats = old.boats ++ unseenBoats,
            invites = old.invites ++ unseenInvites,
            friends = old.friends ++ unseenFriends
          )
        )
      else
        user.email.fold(acc): email =>
          acc :+ UserInfo(
            user.id,
            user.user,
            email,
            user.language,
            newBoats,
            ub.hasCars,
            user.enabled,
            user.added.toEpochMilli,
            newInvites,
            newFriends
          )

  private def collectBoats(rows: Seq[JoinedTrack], formatter: TimeFormatter): Seq[BoatInfo] =
    rows.foldLeft(Vector.empty[BoatInfo]): (acc, row) =>
      val boatIdx =
        acc.indexWhere(b => b.user == row.username && b.boatId == row.boatId)
      val newRow = row.strip(formatter)
      if boatIdx >= 0 then
        val old = acc(boatIdx)
        acc.updated(boatIdx, old.copy(tracks = old.tracks :+ newRow))
      else
        acc :+ BoatInfo(
          row.boatId,
          row.boatName,
          row.username,
          row.language,
          Seq(newRow)
        )

class DoobieUserManager[F[_]](db: DoobieDatabase[F]) extends IdentityManager[F] with DoobieSQL:
  object sql extends CommonSql
  import db.run
  private val userColumns = fr"u.id, u.user, u.email, u.token, u.language, u.enabled, u.added"
  private val selectUsers = sql"select $userColumns from users u"

  private def userById(id: UserId) = sql"$selectUsers where u.id = $id"
  private def userByEmail(email: Email) = sql"$selectUsers where u.email = $email".query[UserRow]
  private def userByName(name: Username) = sql"$selectUsers where u.user = $name"
  private def userByEmailIO(email: Email) = userByEmail(email).unique
  def userMeta(email: Email): F[UserRow] = run:
    userByEmailIO(email)
  def register(email: Email): F[UserRow] = run:
    for
      _ <- getOrCreate(email)
      user <- userByEmailIO(email)
    yield user
  def userInfo(email: Email): F[UserInfo] = run:
    for
      userId <- getOrCreate(email)
      info <- idToUser(userId)
    yield info

  override def tokenToUser(token: UserToken): F[UserInfo] = run:
    sql"""select u.id from users u where u.token = $token"""
      .query[UserId]
      .option
      .flatMap: opt =>
        opt
          .map: userId =>
            idToUser(userId)
          .getOrElse:
            fail(IdentityException(InvalidCredentials(None)))

  private def idToUser(id: UserId): ConnectionIO[UserInfo] =
    sql"""select u.id,
                     u.user,
                     u.email,
                     u.token,
                     u.language,
                     u.enabled,
                     u.added,
                     b.id boatId,
                     b.name boatName,
                     b.source_type sourceType,
                     b.token boatToken,
                     b.gps_ip,
                     b.gps_port,
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
                     fub.added as fubAdded,
                     not isnull(rt.id) as hasCars
              from users u
              left join boats b on b.owner = u.id
              left join users_boats ub on u.id = ub.user
              left join boats ubb on ub.boat = ubb.id
              left join users_boats fub on fub.boat = b.id
              left join boats fubb on fub.boat = fubb.id
              left join users fu on fub.user = fu.id
              left join refresh_tokens rt on rt.owner = u.id and rt.service = ${RefreshService.Polestar}
              where u.id = $id"""
      .query[JoinedUser]
      .to[List]
      .map(DoobieUserManager.collectUsers)
      .flatMap[UserInfo]: infos =>
        infos.headOption
          .map: profile =>
            // Type annotation helps here for some reason
            val checked: ConnectionIO[UserInfo] =
              if profile.enabled then pure(profile)
              else fail(IdentityException(UserDisabled(profile.username)))
            checked
          .getOrElse:
            fail(IdentityException(InvalidCredentials(None)))

  def authBoat(token: BoatToken): F[JoinedSource] = run:
    CommonSql
      .boatsByToken(token)
      .flatMap: opt =>
        opt.map(b => pure(b)).getOrElse(fail(IdentityException(InvalidToken(token))))

  def boats(email: Email, limits: LimitLike): F[UserBoats] = run:
    def tracksIO(id: UserId) =
      val baseQuery = sql.nonEmptyTracks(Option(limits))
      sql"""$baseQuery and (b.uid = $id or b.id in (select ub.boat from users_boats ub where ub.user = $id and ub.state = $accepted)) and t.points > 10"""
        .query[JoinedTrack]
        .to[List]

    def deviceRowsIO(email: Email) =
      sql"""${sql.boats} and u.email = $email and b.id not in (select boat from tracks)"""
        .query[JoinedSource]
        .to[List]
    for
      id <- getOrCreate(email)
      user <- userById(id).query[UserRow].unique
      userTracks <- tracksIO(id)
      devices <- deviceRowsIO(email)
    yield
      val bs = collectBoats(userTracks, TimeFormatter.lang(user.language))
      val gpsDevices = devices.map(d => BoatInfo(d.device, d.boatName, d.username, d.language, Nil))
      UserBoats(user.user, user.language, bs ++ gpsDevices)

  def initUser(user: Username = Usernames.anon): F[NewUser] = run:
    val anon = NewUser(user, None, UserToken.random(), enabled = true)
    for
      exists <- userByName(user).query[UserRow].option
      _ <- exists.map(u => pure(u.user)).getOrElse(userInsertion(anon))
    yield anon

  def addUser(user: NewUser): F[Either[AlreadyExists, UserRow]] = run:
    val email = user.email.fold("no email")(_.value)
    val describe = s"user '${user.user}' with $email"
    log.info(s"Adding $describe...")
    userInsertion(user)
      .flatMap: uid =>
        log.info(s"Added $describe and ID $uid.")
        userById(uid).query[UserRow].unique
      .map[Either[AlreadyExists, UserRow]](u => Right(u))
      .exceptSql: sqle =>
        if sqle.getMessage.contains("primary key violation") then
          pure(Left(AlreadyExists(user.user)))
        else fail(sqle)

  def deleteUser(user: Username): F[Either[UserDoesNotExist, Unit]] = run:
    sql"""delete from users where user = $user""".update.run.map: changed =>
      if changed > 0 then
        log.info(s"Deleted user '$user'.")
        Right(())
      else Left(UserDoesNotExist(user))

  def changeLanguage(user: UserId, to: Language): F[Boolean] = run:
    sql"""update users set language = $to where id = $user""".update.run.map: changed =>
      val wasChanged = changed > 0
      if wasChanged then log.info(s"Changed language of user ID '$user' to '$to'.")
      wasChanged

  def save(token: RefreshToken, service: RefreshService, user: UserId): F[RefreshRow] = run:
    log.info(s"Saving $service refresh token for '$user'...")
    val tokenId = RefreshTokenId.random()
    val insertion =
      sql"""insert into refresh_tokens(id, refresh_token, owner, service)
            values($tokenId, $token, $user, $service)""".update.run
    insertion.flatMap: _ =>
      log.info(s"Saved $service refresh token with ID '$tokenId' for user '$user'.")
      loadTokenIO(tokenId)

  def remove(token: RefreshTokenId): F[Int] = run:
    sql"""delete from refresh_tokens where id = $token""".update.run

  def removeTokens(user: UserId, service: RefreshService): F[Int] = run:
    sql"""delete from refresh_tokens where owner = $user and service = $service""".update.run.map:
      rows =>
        if rows > 0 then log.info(s"Removed $rows $service refresh token(s) for '$user'.")
        rows

  def load(token: RefreshTokenId): F[RefreshRow] = run(loadTokenIO(token))

  def updateValidation(token: RefreshTokenId): F[RefreshRow] = run:
    val up =
      sql"""update refresh_tokens set last_verification = now() where id = $token""".update.run
    up.flatMap(_ => loadTokenIO(token))

  def refreshTokens(user: UserId, service: RefreshService): F[List[RefreshToken]] = run:
    sql"""select refresh_token from refresh_tokens where owner = $user and service = $service"""
      .query[RefreshToken]
      .to[List]

  private def loadTokenIO(id: RefreshTokenId) =
    sql"""select id, refresh_token, owner, last_verification, now() > date_add(last_verification, interval 1 day) as can_verify, added
          from refresh_tokens
          where id = $id
       """.query[RefreshRow].unique

  def invite(i: InviteInfo): F[InviteResult] = run:
    userByEmail(i.email).option.flatMap: invitee =>
      invitee
        .map(user => addInviteIO(i.boat, user.id, i.principal))
        .getOrElse:
          pure(UnknownEmail(i.email): InviteResult)

  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): F[InviteResult] = run:
    addInviteIO(boat, to, principal)

  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): F[AccessResult] = run:
    manageGroups(boat, from, principal): existed =>
      if existed then
        sql"delete from users_boats where boat = $boat and user = $from".update.run
          .map: changed =>
            if changed == 1 then AccessResult(existed)
            else AccessResult(false)
      else pure(AccessResult(existed))

  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): F[Long] = run:
    sql"update users_boats set state = ${state.name} where boat = $boat and user = $user".update.run
      .map(_.toLong)

  private def linkInvite(boat: DeviceId, to: UserId): ConnectionIO[Int] =
    sql"""insert into users_boats(user, boat, state)
          values($to, $boat, ${InviteState.awaiting})""".update.run

  private def userInsertion(user: NewUser): ConnectionIO[UserId] =
    sql"""insert into users(user, email, token, language, enabled)
          values(${user.user}, ${user.email}, ${user.token}, ${Language.default}, ${user.enabled})""".update
      .withUniqueGeneratedKeys[UserId]("id")
      .map: userId =>
        log.info(s"Created user '${user.user}' with ID '$userId'.")
        userId

  private def getOrCreate(email: Email): ConnectionIO[UserId] =
    for
      existing <- userByEmail(email).option
      userId <- existing.map(u => pure(u.id)).getOrElse(addUserWithBoat(email))
    yield userId

  private def addUserWithBoat(email: Email) =
    log.info(s"Adding user '$email' with a randomly generated boat...")
    for
      userId <- userInsertion(NewUser.email(email))
      _ = log.info(s"Added user '$email'.")
      _ <- boatInsertion(userId)
    yield userId

  private def boatInsertion(owner: UserId) =
    sql"""insert into boats(name, token, owner)
          values(${BoatNames.random()}, ${BoatTokens.random()}, $owner)""".update.run

  private def addInviteIO(
    boat: DeviceId,
    to: UserId,
    principal: UserId
  ): ConnectionIO[InviteResult] =
    manageGroups(boat, to, principal): existed =>
      if existed then pure(AlreadyInvited(to, boat))
      else linkInvite(boat, to).map(_ => Invited(to, boat))

  private def manageGroups[T](boat: DeviceId, from: UserId, principal: UserId)(
    run: Boolean => ConnectionIO[T]
  ): ConnectionIO[T] =
    for
      owns <-
        sql"""select b.id from boats b where b.id = $boat and b.owner = $principal"""
          .query[DeviceId]
          .option
      _ <-
        owns
          .map(pure)
          .getOrElse(fail(PermissionException(principal, boat, from)))
      link <-
        sql"select ub.user, u.email from users_boats ub, users u where ub.user = u.id and ub.boat = $boat and ub.user = $from"
          .query[UserId]
          .option
      res <- run(link.isDefined)
    yield res
