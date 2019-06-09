package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{BoatToken, JoinedBoat, Language, UserBoats, UserInfo, UserToken}
import com.malliina.values.{Email, UserId, Username}

import scala.concurrent.Future

object PassThroughUserManager extends UserManager {

  val godUser = DataUser(UserId(1L),
                         Username("test"),
                         None,
                         UserToken.random(),
                         Language.english,
                         enabled = true,
                         added = Instant.now())
  val god = UserInfo(
    UserId(1L),
    Username("test"),
    Email("a@b.com"),
    Language.default,
    Nil,
    enabled = true,
    addedMillis = Instant.now().toEpochMilli
  )

  def userMeta(email: Email): Future[DataUser] = fut(godUser)

  def userInfo(email: Email): Future[UserInfo] = fut(god)

  def authBoat(token: BoatToken): Future[JoinedBoat] =
    Future.failed(IdentityException(InvalidToken(token)))

  def boats(user: Email): Future[UserBoats] = fut(UserBoats.anon)

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserId]] =
    fut(Left(AlreadyExists(user.username)))

  def deleteUser(user: Username): Future[Either[UserDoesNotExist, Unit]] =
    fut(Left(UserDoesNotExist(user)))

  override def changeLanguage(user: UserId, to: Language): Future[Boolean] =
    fut(false)

  def users: Future[Seq[UserInfo]] = fut(Seq(god))

  def fut[T](t: T) = Future.successful(t)
}
