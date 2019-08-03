package com.malliina.boat.db

import java.time.Instant

import com.malliina.boat.{MobileDevice, PushId, PushToken}
import com.malliina.boat.db.BoatSchema.CreatedTimestampType
import com.malliina.values.UserId

trait PushSchema extends Mappings with DatabaseClient { self: JdbcComponent =>
  import api._

  val pushTable = TableQuery[PushClientsTable]
  val pushInserts = pushTable.map(_.forInserts).returning(pushTable.map(_.id))

  class PushClientsTable(tag: Tag) extends Table[PushDevice](tag, "push_clients") {
    def id = column[PushId]("id", O.PrimaryKey, O.AutoInc)
    def token = column[PushToken]("token", O.Unique, O.Length(1024))
    def device = column[MobileDevice]("device", O.Length(128))
    def user = column[UserId]("user", O.Length(128))
    def added = column[Instant]("added", O.SqlType(CreatedTimestampType))

    def forInserts = (token, device, user).mapTo[PushInput]
    def * = (id, token, device, user, added) <> ((PushDevice.apply _).tupled, PushDevice.unapply)
  }
}
