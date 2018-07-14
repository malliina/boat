package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{BoatInfo, BoatToken, UserToken}
import com.malliina.values.{Email, Password, UserId, Username}

import scala.concurrent.Future

object PassThroughUserManager extends UserManager {
  val god = DataUser(UserId(1L), Username("test"), None, "", UserToken.random(), enabled = true, added = Instant.now())

  def authenticate(user: Username, pass: Password): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authUser(token: UserToken): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authEmail(email: Email): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]] = fut(Left(InvalidToken(token)))

  def boats(user: Email): Future[Seq[BoatInfo]] = fut(Nil)

  def updatePassword(user: Username, newPass: Password): Future[Unit] = fut(())

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]] = fut(Left(AlreadyExists(user.username)))

  def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] = fut(Left(UserDoesNotExist(user)))

  def users: Future[Seq[DataUser]] = fut(Seq(god))

  def fut[T](t: T) = Future.successful(t)
}
