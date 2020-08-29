package com.malliina.boat.db

import com.malliina.values.{Email, UserId}
import doobie.implicits._
import DoobieMappings._
import com.malliina.boat.{BoatToken, JoinedBoat}

import scala.concurrent.Future

class DoobieUserManager(db: DoobieDatabase) {
  val selectUsers = sql"select id, user, email, token, language, enabled, added from users u"
  def userById(id: UserId) = sql"$selectUsers where u.id = $id"
  def userByEmail(email: Email) = sql"$selectUsers where u.email = $email"

  def userMeta(email: Email): Future[UserRow] = db.run {
    userByEmail(email).query[UserRow].unique
  }

  def authBoat(token: BoatToken): Future[JoinedBoat] = db.run {
    sql"""select b.id, b.name, b.token, u.id uid, u.user, u.email, u.language 
          from boats b, users u 
          where b.owner = u.id and b.token = $token"""
      .query[JoinedBoat]
      .unique
  }

  def addUser(user: NewUser): Future[Either[AlreadyExists, UserRow]] = db.run {
    val insertion =
      sql"""insert into users(user, email, token, enabled) 
            values(${user.user}, ${user.email}, ${user.token}, ${user.enabled})""".update
        .withUniqueGeneratedKeys[UserId]("id")
    for {
      id <- insertion
      row <- userById(id).query[UserRow].unique
    } yield Right(row)
  }
}
