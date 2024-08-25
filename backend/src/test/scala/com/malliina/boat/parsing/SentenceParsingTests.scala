package com.malliina.boat.parsing

import cats.effect.IO
import com.malliina.boat.{BaseSuite, Coord, KeyedSentence, RawSentence, SentenceKey}
import com.malliina.measure.{DistanceIntM, SpeedIntM, TemperatureInt}
import com.malliina.values.lngLat

import java.time.{LocalDate, LocalTime}

class SentenceParsingTests extends BaseSuite:
  test("stateful sentence parsing"):
    val from = MultiParsingTests.testFrom

    def keyed(id: Long) = KeyedSentence(SentenceKey(id), RawSentence(""), from)

    val testTemp = WaterTemperature(10.celsius, keyed(1))
    val testSpeed = ParsedBoatSpeed(40.knots, keyed(2))
    val testDepth = WaterDepth(10.meters, 0.meters, keyed(3))
    val parsed = fs2.Stream[IO, ParsedSentence](
      testTemp,
      testSpeed,
      testDepth,
      ParsedDateTime(LocalDate.of(2018, 4, 10), LocalTime.of(10, 11, 1), keyed(4)),
      ParsedCoord(1.0 lngLat 2.0, LocalTime.of(10, 11, 1), keyed(5)),
      ParsedDateTime(LocalDate.of(2018, 4, 10), LocalTime.of(10, 12, 2), keyed(6)),
      ParsedCoord(4.0 lngLat 5.0, LocalTime.of(10, 12, 2), keyed(7)),
      ParsedDateTime(LocalDate.of(2018, 4, 11), LocalTime.of(10, 13, 3), keyed(8)),
      ParsedCoord(6.0 lngLat 7.0, LocalTime.of(10, 13, 3), keyed(9)),
      ParsedCoord(8.0 lngLat 9.0, LocalTime.of(0, 1, 4), keyed(10))
    )

    def toFull(coord: Coord, time: LocalTime, date: LocalDate, keys: Seq[Long]) =
      FullCoord(
        coord,
        time,
        date,
        testSpeed.speed,
        BoatStats(
          testTemp.temp,
          testDepth.depth,
          testDepth.offset,
          keys.map(SentenceKey.apply)
        ),
        from
      )

    val expected = List(
      toFull(
        1.0 lngLat 2.0,
        LocalTime.of(10, 11, 1),
        LocalDate.of(2018, 4, 10),
        Seq(5, 4, 2, 1, 3)
      ),
      toFull(
        4.0 lngLat 5.0,
        LocalTime.of(10, 12, 2),
        LocalDate.of(2018, 4, 10),
        Seq(7, 6, 2, 1, 3)
      ),
      toFull(
        6.0 lngLat 7.0,
        LocalTime.of(10, 13, 3),
        LocalDate.of(2018, 4, 11),
        Seq(9, 8, 2, 1, 3)
      ),
      toFull(
        8.0 lngLat 9.0,
        LocalTime.of(10, 13, 3),
        LocalDate.of(2018, 4, 11),
        Seq(10, 8, 2, 1, 3)
      )
    )
    val manager = TrackManager()
    val processed = parsed.flatMap(s => fs2.Stream.emits(manager.update(s))).take(4)
    val actual = processed.compile.toList.unsafeRunSync()
    assert(actual == expected)
