package com.malliina.boat

import cats.Show
import com.malliina.measure.DistanceM
import com.malliina.values.{ErrorMessage, JsonCompanion}
import com.malliina.values.Literals.err
import io.circe.Codec
import org.typelevel.ci.CIString
import com.malliina.measure.DistanceIntM

opaque type RegistrationNumber = CIString
object RegistrationNumber extends CICompanion[RegistrationNumber]:
  val Key = "registrationNumber"
  override def apply(raw: CIString): RegistrationNumber = raw
  override def write(t: RegistrationNumber): CIString = t

opaque type VIN = CIString
object VIN extends CICompanion[VIN]:
  override def apply(str: CIString): VIN = str

  override def build(input: CIString): Either[ErrorMessage, VIN] =
    if input.toString.isBlank then Left(err"VIN must not be blank.")
    else Right(input.trim)

  override def write(t: VIN): CIString = t

abstract class CICompanion[T] extends JsonCompanion[CIString, T]:
  given Show[T] = Show.show(t => write(t).toString)

trait VINSpec:
  def vin: VIN

case class Health(
  vin: VIN,
  daysToService: Int,
  brakeFluidLevelWarning: String,
  distanceToServiceKm: Int,
  timestamp: Updated
) extends VINSpec derives Codec.AsObject

case class Updated(seconds: String, nanos: Long) derives Codec.AsObject

case class Battery(
  vin: VIN,
  batteryChargeLevelPercentage: Int,
  chargingStatus: String,
  estimatedDistanceToEmptyKm: Int,
  timestamp: Updated
) extends VINSpec derives Codec.AsObject

case class Odometer(
  vin: VIN,
  odometerMeters: Int,
  timestamp: Updated
) extends VINSpec derives Codec.AsObject:
  def odometer: DistanceM = odometerMeters.meters

case class CarTelematics(health: Health, battery: Battery, odometer: Odometer)
  derives Codec.AsObject

case class CarsTelematics(health: Seq[Health], battery: Seq[Battery], odometer: Seq[Odometer])
  derives Codec.AsObject:
  def forVin(vin: VIN): Either[ErrorMessage, CarTelematics] =
    def findVIN[T <: VINSpec](ts: Seq[T], label: String): Either[ErrorMessage, T] =
      ts.find(_.vin == vin).toRight(ErrorMessage(s"No $label for VIN: '$vin'."))
    for
      h <- findVIN(health, "health")
      b <- findVIN(battery, "battery")
      o <- findVIN(odometer, "odometer")
    yield CarTelematics(h, b, o)
