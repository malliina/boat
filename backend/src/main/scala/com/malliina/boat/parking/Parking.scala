package com.malliina.boat.parking

import cats.effect.Sync
import com.malliina.boat.{LocalConf, Resources}
import io.circe.Json
import io.circe.parser.decode

import java.nio.file.Files

object Parking extends Resources:
  private val localFile = LocalConf.appDir.resolve("parking-areas.json")

  private def parkingFile = Resources.file("parking-areas.json", localFile)

  def load[F[_]: Sync]: F[Json] =
    val F = Sync[F]
    F.rethrow(F.delay(decode[Json](Files.readString(parkingFile))))
