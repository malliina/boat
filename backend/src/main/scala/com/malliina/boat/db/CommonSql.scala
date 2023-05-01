package com.malliina.boat.db

import com.malliina.boat.{BoatToken, DeviceId, JoinedSource}
import doobie.*
import doobie.implicits.*
import com.malliina.boat.db.Mappings.*

object CommonSql extends CommonSql

trait CommonSql:
  val boats =
    sql"""select b.id, b.name, b.source_type, b.token, u.id uid, u.user, u.email, u.language
          from boats b, users u
          where b.owner = u.id"""
  def boatsByToken(token: BoatToken) =
    sql"""$boats and b.token = $token""".query[JoinedSource].option
  def boatsById(id: DeviceId) = sql"$boats and b.id = $id".query[JoinedSource].unique
  private val topPoints =
    sql"""select winners.track track, min(winners.id) point
          from (select p.id, p.track
                from points p,
                    (select track, max(speed) maxSpeed from points group by track) tops
                where p.track = tops.track and p.speed = tops.maxSpeed) winners
          group by winners.track"""
  val selectAllPoints =
    sql"""select id, longitude, latitude, coord, speed, outside_temperature, water_temp, depthm, depth_offsetm, source_time, track, track_index, diff, added
          from points p"""
  val topRows =
    sql"""$selectAllPoints where p.id in (select point from ($topPoints) fastestPoints)"""
  private val minMaxTimes =
    sql"""select track,
                 min(source_time)                                        start,
                 max(source_time)                                        end,
                 timestampdiff(SECOND, min(source_time), max(source_time)) secs,
                 date(min(source_time))                                  startDate,
                 month(min(source_time))                                 startMonth,
                 year(min(source_time))                                  startYear
          from points p
          group by track"""
  private val timedTracks =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, times.secs secs, times.start, times.end, times.startDate, times.startMonth, times.startYear, t.boat
           from tracks t,
           ($minMaxTimes) times
           where t.id = times.track"""
  private val trackHighlights =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, t.start, t.end, t.secs, t.startDate, t.startMonth, t.startYear, t.boat, top.id pointId, top.longitude, top.latitude, top.coord, top.speed, top.outside_temperature, top.water_temp, top.depthm, top.depth_offsetm, top.source_time, date(top.source_time) trackDate, top.track, top.added topAdded
          from ($topRows) top, ($timedTracks) t
          where top.track = t.id"""
  private val trackColumns =
    fr0"t.id tid, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, t.start, t.startDate, t.startMonth, t.startYear, t.end, t.secs duration, t.speed maxBoatspeed, t.pointId, t.longitude, t.latitude, t.coord, t.speed topSpeed, t.outside_temperature, t.water_temp, t.depthm, t.depth_offsetm, t.source_time, t.trackDate, t.track, t.topAdded, b.id boatId, b.name boatName, b.source_type, b.token, b.uid, b.user owner, b.email, b.language"
  val nonEmptyTracks = nonEmptyTracksWith(trackColumns)
  def nonEmptyTracksWith(cols: Fragment) =
    sql"""select $cols
          from ($boats) b, ($trackHighlights) t
          where b.id = t.boat"""

  val pointColumns =
    fr"p.id, p.longitude, p.latitude, p.coord, p.speed, p.outside_temperature, p.water_temp, p.depthm, p.depth_offsetm, p.source_time, date(p.source_time), p.track, p.added"
