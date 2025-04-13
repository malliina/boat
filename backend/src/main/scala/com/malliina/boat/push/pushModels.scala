package com.malliina.boat.push

import com.malliina.boat.http.Named
import com.malliina.boat.{BoatName, BoatPrimitives, ReverseGeocode}
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
  state: SourceState,
  distance: DistanceM,
  duration: FiniteDuration,
  geo: Option[ReverseGeocode]
) derives Codec.AsObject:
  private val describeState = if state == SourceState.Connected then "on the move" else state.name
  def prefix = s"$boatName $describeState"
  def suffix = geo.fold("")(a => s" at ${a.address}")
  def message = s"$prefix$suffix"

object SourceNotification:
  val Message = "message"
  val Title = "title"

given Codec[FiniteDuration] = BoatPrimitives.durationFormat

case class LiveActivityState(
  boatName: BoatName,
  message: String,
  distance: DistanceM,
  duration: FiniteDuration
) derives Codec.AsObject

object LiveActivityState:
  val attributeType = "BoatWidgetAttributes"
