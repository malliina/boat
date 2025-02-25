package com.malliina.boat.push

import com.malliina.boat.{BoatName, ReverseGeocode}
import com.malliina.boat.http.Named
import com.malliina.values.{ErrorMessage, ValidatingCompanion}
import io.circe.Codec

sealed abstract class SourceState(val name: String) extends Named

object SourceState extends ValidatingCompanion[String, SourceState]:
  val Key = "state"
  val all: Seq[SourceState] = Seq(Connected, Disconnected)

  override def build(input: String): Either[ErrorMessage, SourceState] =
    all.find(_.name == input).toRight(ErrorMessage(s"Unknown boat state: '$input"))

  override def write(t: SourceState): String = t.name

  case object Connected extends SourceState("connected")
  case object Disconnected extends SourceState("disconnected")

case class SourceNotification(
  title: String,
  boatName: BoatName,
  state: SourceState,
  geo: Option[ReverseGeocode]
) derives Codec.AsObject:
  private val describeState = if state == SourceState.Connected then "on the move" else state.name
  def prefix = s"$boatName $describeState"
  def suffix = geo.fold("")(a => s" at ${a.address}")
  def message = s"$prefix$suffix"

object SourceNotification:
  val Message = "message"
  val Title = "title"
