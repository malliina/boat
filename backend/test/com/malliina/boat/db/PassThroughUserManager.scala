package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{BoatInfo, BoatToken, User, UserEmail, UserId, UserToken}
import com.malliina.play.models.Password

import scala.concurrent.Future

object PassThroughUserManager extends UserManager {
  val god = DataUser(UserId(1L), User("test"), None, "", UserToken.random(), enabled = true, added = Instant.now())

  def authenticate(user: User, pass: Password): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authUser(token: UserToken): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authEmail(email: UserEmail): Future[Either[IdentityError, DataUser]] = fut(Right(god))

  def authBoat(token: BoatToken): Future[Either[IdentityError, BoatInfo]] = fut(Left(InvalidToken(token)))

  def boats(user: UserEmail): Future[Seq[BoatInfo]] = fut(Nil)

  def updatePassword(user: User, newPass: Password): Future[Unit] = fut(())

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]] = fut(Left(AlreadyExists(user.username)))

  def deleteUser(user: User): Future[Either[UserDoesNotExist, Unit]] = fut(Left(UserDoesNotExist(user)))

  def users: Future[Seq[DataUser]] = fut(Seq(god))

  def fut[T](t: T) = Future.successful(t)
}
