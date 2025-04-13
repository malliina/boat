package com.malliina.boat.push

import com.malliina.boat.http.Named
import com.malliina.boat.{BoatName, BoatPrimitives, PushLang, ReverseGeocode, TrackName}
import com.malliina.measure.DistanceM
import com.malliina.values.{ErrorMessage, ValidatingCompanion}
import io.circe.Codec

import scala.concurrent.duration.FiniteDuration

enum SourceState(val name: String) extends Named:
  case Connected extends SourceState("connected")
  case Disconnected extends SourceState("disconnected")

object SourceState extends ValidatingCompanion[String, SourceState]:
  val Key = "state"
  val all: Seq[SourceState] = Seq(Connected, Disconnected)

  override def build(input: String): Either[ErrorMessage, SourceState] =
    all.find(_.name == input).toRight(ErrorMessage(s"Unknown boat state: '$input"))

  override def write(t: SourceState): String = t.name

case class SourceNotification(
  title: String,
  boatName: BoatName,
  trackName: TrackName,
  state: SourceState,
  distance: DistanceM,
  duration: FiniteDuration,
  geo: Option[ReverseGeocode],
  lang: PushLang
) derives Codec.AsObject:
  def message =
    val describe = if state == SourceState.Connected then lang.onTheMove else lang.stoppedMoving
    val prefix = s"$boatName $describe"
    val suffix = geo.fold("")(a => s" ${lang.near} ${a.address}")
    s"$prefix$suffix"

object SourceNotification:
  val Message = "message"
  val Title = "title"

given Codec[FiniteDuration] = BoatPrimitives.durationFormat

case class LiveActivityAttributes(boatName: BoatName, trackName: TrackName) derives Codec.AsObject

object LiveActivityAttributes:
  val attributeType = "BoatWidgetAttributes"

case class LiveActivityState(
  message: String,
  distance: DistanceM,
  duration: FiniteDuration,
  address: Option[String]
) derives Codec.AsObject
