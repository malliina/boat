package com.malliina.boat.db

import doobie.Fragment
import doobie.implicits._

object CommonSql extends CommonSql

trait CommonSql {
  val boats =
    sql"""select b.id, b.name, b.token, u.id uid, u.user, u.email, u.language
          from boats b, users u 
          where b.owner = u.id"""
  val topPoints =
    sql"""select winners.track track, min(winners.id) point
          from (select p.id, p.track
                from points p,
                    (select track, max(boat_speed) maxSpeed from points group by track) tops
                where p.track = tops.track and p.boat_speed = tops.maxSpeed) winners
          group by winners.track"""
  val selectAllPoints =
    sql"""select id, longitude, latitude, coord, boat_speed, water_temp, depthm, depth_offsetm, boat_time, track, track_index, diff, added 
          from points p"""
  val topRows =
    sql"""$selectAllPoints where p.id in (select point from ($topPoints) fastestPoints)"""
  val minMaxTimes =
    sql"""select track,
                 min(boat_time)                                        start,
                 max(boat_time)                                        end,
                 timestampdiff(SECOND, min(boat_time), max(boat_time)) secs,
                 date(min(boat_time))                                  startDate,
                 month(min(boat_time))                                 startMonth,
                 year(min(boat_time))                                  startYear
          from points p
          group by track"""
  val timedTracks =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, times.secs secs, times.start, times.end, times.startDate, times.startMonth, times.startYear, t.boat
           from tracks t,
           ($minMaxTimes) times 
           where t.id = times.track"""
  val trackHighlights =
    sql"""select t.id, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, t.start, t.end, t.secs, t.startDate, t.startMonth, t.startYear, t.boat, top.id pointId, top.longitude, top.latitude, top.coord, top.boat_speed, top.water_temp, top.depthm, top.depth_offsetm, top.boat_time, date(top.boat_time) trackDate, top.track, top.added topAdded
          from ($topRows) top, ($timedTracks) t
          where top.track = t.id"""
  val trackColumns =
    fr0"t.id tid, t.name, t.title, t.canonical, t.comments, t.added, t.points, t.avg_speed, t.avg_water_temp, t.distance, t.start, t.startDate, t.startMonth, t.startYear, t.end, t.secs duration, t.boat_speed maxBoatspeed, t.pointId, t.longitude, t.latitude, t.coord, t.boat_speed topSpeed, t.water_temp, t.depthm, t.depth_offsetm, t.boat_time, t.trackDate, t.track, t.topAdded, b.id boatId, b.name boatName, b.token, b.uid, b.user owner, b.email, b.language"
  val nonEmptyTracks = nonEmptyTracksWith(trackColumns)
  def nonEmptyTracksWith(cols: Fragment) =
    sql"""select $cols
          from ($boats) b, ($trackHighlights) t
          where b.id = t.boat"""

  val pointColumns =
    fr"p.id, p.longitude, p.latitude, p.coord, p.boat_speed, p.water_temp, p.depthm, p.depth_offsetm, p.boat_time, date(p.boat_time), p.track, p.added"
}
