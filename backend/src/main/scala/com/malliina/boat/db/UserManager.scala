package com.malliina.boat.db

import com.malliina.boat.http.{AccessResult, InviteInfo, InviteResult}
import com.malliina.boat.{BoatToken, DeviceId, InviteState, JoinedSource, Language, UserBoats, UserInfo}
import com.malliina.values.*
import org.apache.commons.codec.digest.DigestUtils

trait IdentityManager[F[_]] extends UserManager[F] with TokenManager[F]

trait UserManager[F[_]]:
  def userMeta(email: Email): F[UserRow]

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
  def userInfo(email: Email): F[UserInfo]
  def authBoat(token: BoatToken): F[JoinedSource]
  def boats(user: Email): F[UserBoats]
  def addUser(user: NewUser): F[Either[AlreadyExists, UserRow]]
  def deleteUser(user: Username): F[Either[UserDoesNotExist, Unit]]
  def changeLanguage(user: UserId, to: Language): F[Boolean]
  def invite(i: InviteInfo): F[InviteResult]
  def grantAccess(boat: DeviceId, to: UserId, principal: UserId): F[InviteResult]
  def revokeAccess(boat: DeviceId, from: UserId, principal: UserId): F[AccessResult]
  def updateInvite(boat: DeviceId, user: UserId, state: InviteState): F[Long]

  protected def hash(user: Username, pass: Password): String =
    DigestUtils.md5Hex(s"$user:${pass.pass}")

trait TokenManager[F[_]]:
  def register(email: Email): F[UserRow]
  def save(token: RefreshToken, user: UserId): F[RefreshRow]
  def remove(token: RefreshTokenId): F[Int]
  def load(token: RefreshTokenId): F[RefreshRow]
  def updateValidation(token: RefreshTokenId): F[RefreshRow]
  def refreshTokens(user: UserId): F[List[RefreshToken]]
