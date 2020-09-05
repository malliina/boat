package com.malliina.boat.db

import com.malliina.boat.{Boat, BoatInfo, JoinedTrack, TimeFormatter, UserInfo}
import play.api.Logger

object NewUserManager {
  private val log = Logger(getClass)

  def collect(rows: Seq[JoinedUser]) = collectUsers(rows.map(r => (r.user, r.boat)))

  def collectUsers(rows: Seq[(UserRow, Option[BoatRow])]): Vector[UserInfo] =
    rows.foldLeft(Vector.empty[UserInfo]) {
      case (acc, (user, boat)) =>
        val idx = acc.indexWhere(_.id == user.id)
        val newBoats =
          boat.toSeq.map(b => Boat(b.id, b.name, b.token, b.added.toEpochMilli))
        if (idx >= 0) {
          val old = acc(idx)
          acc.updated(idx, old.copy(boats = old.boats ++ newBoats))
        } else {
          user.email.fold(acc) { email =>
            acc :+ UserInfo(
              user.id,
              user.user,
              email,
              user.language,
              newBoats,
              user.enabled,
              user.added.toEpochMilli
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
