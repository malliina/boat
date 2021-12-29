package com.malliina.boat.db

import cats.effect.IO
import com.malliina.boat.http.{AccessResult, InviteInfo, InviteResult}
import com.malliina.boat.{BoatToken, DeviceId, InviteState, JoinedBoat, Language, UserBoats, UserInfo}
import com.malliina.values.*
import org.apache.commons.codec.digest.DigestUtils

trait IdentityManager extends UserManager with TokenManager

trait UserManager:
  def userMeta(email: Email): IO[UserRow]

  /** Retrieves user information for the user with the given email address. If the user does not
    * exist, one is created with the email address as the username, and with a newly created
    * randomly named boat. This enables user login without an explicit signup step.
    *
    * The email address is expected to be in possession of the user, meaning we have extracted it
    * from a validated Google ID token when calling this method.
    *
    * @param email
    *   email address of the user
    * @return
    *   user info for `email`
    */
  def userInfo(email: Email): IO[UserInfo]
  def authBoat(token: BoatToken): IO[JoinedBoat]
  def boats(user: Email): IO[UserBoats]
  def addUser(user: NewUser): IO[Either[AlreadyExists, UserRow]]
  def deleteUser(user: Username): IO[Either[UserDoesNotExist, Unit]]
  def changeLanguage(user: UserId, to: Language): IO[Boolean]
  def invite(i: InviteInfo): IO[InviteResult]
  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): IO[InviteResult]
  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): IO[AccessResult]
  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): IO[Long]

  protected def hash(user: Username, pass: Password): String =
    DigestUtils.md5Hex(s"$user:${pass.pass}")

trait TokenManager:
  def register(email: Email): IO[UserRow]
  def save(token: RefreshToken, user: UserId): IO[RefreshTokenId]
  def remove(token: RefreshTokenId): IO[Int]
