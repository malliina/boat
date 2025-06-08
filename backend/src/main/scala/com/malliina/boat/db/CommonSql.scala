package com.malliina.boat.db

import com.malliina.boat.{BoatToken, DeviceId, JoinedSource, JoinedTrack}
import doobie.*
import doobie.implicits.*
import com.malliina.boat.db.Mappings.given
import com.malliina.boat.http.LimitLike

object CommonSql extends CommonSql

trait CommonSql:
  val boats =
    sql"""select ${JoinedSource.columns}
          from boats b, users u
          where b.owner = u.id"""
  def boatsByToken(token: BoatToken): ConnectionIO[Option[JoinedSource]] =
    sql"""$boats and b.token = $token""".query[JoinedSource].option
  def boatsById(id: DeviceId) = sql"$boats and b.id = $id".query[JoinedSource].unique
  // The latter part of the union includes tracks with points but no speed
  private val topPoints =
    sql"""select winners.track track, min(winners.point) point
          from (select p.id point, p.track
                from points p,
                     (select track, max(speed) maxSpeed from points group by track) tops
                where p.track = tops.track
                  and p.speed = tops.maxSpeed
                union
                select p2.id point, p2.track
                from points p2,
                     (select track, max(speed) maxSpeed from points group by track) tops2
                where p2.track = tops2.track
                  and tops2.maxSpeed is null) winners
          group by winners.track"""
  private val latestPoints =
    sql"""select p.track, max(p.id) point
          from points p,
               (select track, max(added) latest from points group by track) latest
          where p.track = latest.track
            and p.added = latest.latest
          group by p.track"""
  val selectAllPoints =
    sql"""select id, longitude, latitude, coord, speed, altitude, outside_temperature, water_temp, depthm, depth_offsetm, battery, car_range, source_time, track, track_index, diff, added
          from points p"""
  private val topRows =
    sql"""$selectAllPoints where p.id in (select point from ($topPoints) fastestPoints)"""
  private val latestRows =
    sql"""$selectAllPoints where p.id in (select point from ($latestPoints) latestPoints)"""
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
  private def timedTracks(limits: Option[LimitLike]) =
    val limitClause = limits.fold(Fragment.empty)(l => fr" limit ${l.limit} offset ${l.offset}")
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.avg_outside_temp, t.distance, times.secs secs, times.start, times.end, times.startDate, times.startMonth, times.startYear, t.boat
           from tracks t,
           ($minMaxTimes) times
           where t.id = times.track order by t.added desc$limitClause"""
  private def trackHighlights(limits: Option[LimitLike], topQuery: Fragment) =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.avg_outside_temp, t.distance, t.start, t.end, t.secs, t.startDate, t.startMonth, t.startYear, t.boat, top.id pointId, top.longitude, top.latitude, top.coord, top.speed, top.altitude, top.outside_temperature, top.water_temp, top.depthm, top.depth_offsetm, top.battery, top.car_range, top.source_time, date(top.source_time) trackDate, top.track, top.added topAdded
          from ($topQuery) top, (${timedTracks(limits)}) t
          where top.track = t.id"""
  def nonEmptyTracks(limits: Option[LimitLike]) =
    nonEmptyTracksWith(JoinedTrack.columns, limits, topRows)
  def nonEmptyTracksLatest(limits: Option[LimitLike]) =
    nonEmptyTracksWith(JoinedTrack.columns, limits, latestRows)
  def nonEmptyTracksWith(cols: Fragment, limits: Option[LimitLike], topQuery: Fragment = topRows) =
    sql"""select $cols
          from ($boats) b, (${trackHighlights(limits, topQuery)}) t
          where b.id = t.boat"""

  val pointColumns =
    fr"p.id, p.longitude, p.latitude, p.coord, p.speed, p.altitude, p.outside_temperature, p.water_temp, p.depthm, p.depth_offsetm, p.battery, p.car_range, p.source_time, date(p.source_time), p.track, p.added"
