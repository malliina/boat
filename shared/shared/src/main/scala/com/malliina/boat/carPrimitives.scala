package com.malliina.boat

import com.malliina.measure.DistanceM
import com.malliina.values.ErrorMessage
import com.malliina.values.Literals.err
import io.circe.{Codec, Decoder, Encoder}
import org.typelevel.ci.CIString
import com.malliina.measure.DistanceIntM

import java.time.Instant

opaque type RegistrationNumber = CIString
object RegistrationNumber extends CICompanion[RegistrationNumber]:
  val Key = "registrationNumber"
  override def build(input: CIString): Either[ErrorMessage, RegistrationNumber] = Right(input)
  override def write(t: RegistrationNumber): CIString = t

opaque type VIN = CIString
object VIN extends CICompanion[VIN]:
  override def build(input: CIString): Either[ErrorMessage, VIN] =
    if input.toString.isBlank then Left(err"VIN must not be blank.")
    else Right(input.trim)

  override def write(t: VIN): CIString = t

trait VINSpec:
  def vin: VIN

case class Health(
  vin: VIN,
  daysToService: Int,
  brakeFluidLevelWarning: BrakeFluidLevelWarning,
  engineCoolantLevelWarning: EngineCoolantLevelWarning,
  serviceWarning: ServiceWarning,
  oilLevelWarning: OilLevelWarning,
  distanceToServiceKm: DistanceM,
  timestamp: Updated
) extends VINSpec derives Encoder.AsObject:
  def updated = timestamp.instant

object Health:
  given Decoder[DistanceM] = Decoder.decodeInt.map(_.kilometers)
  given Decoder[BrakeFluidLevelWarning] = BrakeFluidLevelWarning.decodePolestar
  given Decoder[EngineCoolantLevelWarning] = EngineCoolantLevelWarning.decodePolestar
  given Decoder[ServiceWarning] = ServiceWarning.decodePolestar
  given Decoder[OilLevelWarning] = OilLevelWarning.decodePolestar
  given Decoder[Health] = Decoder.derived[Health]

case class Updated(seconds: String, nanos: Long) derives Codec.AsObject:
  def instant = Instant.ofEpochSecond(seconds.toInt).plusNanos(nanos)

case class Battery(
  vin: VIN,
  batteryChargeLevelPercentage: Percentage, // 0-100
  chargingStatus: ChargingStatus,
  estimatedDistanceToEmptyKm: DistanceM,
  timestamp: Updated
) extends VINSpec derives Encoder.AsObject:
  def range = estimatedDistanceToEmptyKm
  def updated = timestamp.instant

object Battery:
  given Decoder[DistanceM] = Decoder.decodeInt.map(_.kilometers)
  given Decoder[ChargingStatus] = ChargingStatus.decodePolestar
  given Decoder[Battery] = Decoder.derived[Battery]

case class Odometer(
  vin: VIN,
  odometerMeters: DistanceM,
  timestamp: Updated
) extends VINSpec derives Codec.AsObject:
  def odometer: DistanceM = odometerMeters

object Odometer:
  given Decoder[DistanceM] = Decoder.decodeInt.map(_.meters)

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
