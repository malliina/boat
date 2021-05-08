package com.malliina.boat.db

import com.malliina.boat.{Language, UserInfo, UserToken}
import com.malliina.values.{Email, UserId, Username}

import java.time.Instant
import scala.concurrent.Future

trait PassThroughUserManager extends UserManager {
  val godUser = UserRow(
    UserId(1L),
    Username("test"),
    None,
    UserToken.random(),
    Language.english,
    enabled = true,
    added = Instant.now()
  )
  val god = UserInfo(
    UserId(1L),
    Username("test"),
    Email("a@b.com"),
    Language.default,
    Nil,
    enabled = true,
    addedMillis = Instant.now().toEpochMilli,
    Nil,
    Nil
  )

//  override def initUser(user: Username): Future[NewUser] =
//    Future.successful(NewUser(user, None, UserToken.random(), enabled = true))
//  def userMeta(email: Email): Future[UserRow] = fut(godUser)
//  def userInfo(email: Email): Future[UserInfo] = fut(god)
//  def authBoat(token: BoatToken): Future[JoinedBoat] =
//    Future.failed(IdentityException(InvalidToken(token)))
//  def boats(user: Email): Future[UserBoats] = fut(UserBoats.anon)
//  def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]] =
//    fut(Left(AlreadyExists(user.user)))
//  def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] =
//    fut(Left(UserDoesNotExist(user)))
//  override def changeLanguage(user: UserId, to: Language): Future[Boolean] =
//    fut(false)
//  override def invite(i: InviteInfo): Future[AccessResult] =
//    fut(AccessResult(false))
//  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): Future[AccessResult] =
//    fut(AccessResult(false))
//  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): Future[AccessResult] =
//    fut(AccessResult(false))
//  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): Future[Long] =
//    fut(0L)

  def users: Future[Seq[UserInfo]] = fut(Seq(god))

  def fut[T](t: T) = Future.successful(t)
}
