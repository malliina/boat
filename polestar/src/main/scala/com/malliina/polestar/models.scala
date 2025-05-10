package com.malliina.polestar

import cats.Show
import com.malliina.http.FullUrl
import com.malliina.measure.{DistanceIntM, DistanceM}
import com.malliina.values.Literals.err
import com.malliina.values.{ErrorMessage, JsonCompanion}
import io.circe.Codec

import java.time.{Instant, LocalDate}

opaque type VIN = String

object VIN extends JsonCompanion[String, VIN]:
  given Show[VIN] = Show(t => write(t))

  override def apply(str: String): VIN = str

  override def build(input: String): Either[ErrorMessage, VIN] =
    if input.isBlank then Left(err"VIN must not be blank.")
    else Right(input.trim)

  override def write(t: VIN): String = t

case class OpenIdConfiguration(authorization_endpoint: FullUrl, token_endpoint: FullUrl)
  derives Codec.AsObject:
  def authorizationEndpoint = authorization_endpoint
  def tokenEndpoint = token_endpoint

case class VINVariable(vin: VIN) derives Codec.AsObject
case class GraphQuery(query: String, variables: Option[VINVariable]) derives Codec.AsObject
object GraphQuery:
  def vin(query: String, vin: VIN) = apply(query, Option(VINVariable(vin)))

case class CarExterior(code: String, name: String) derives Codec.AsObject
case class CarInterior(code: String, name: String) derives Codec.AsObject
case class CarWheels(code: String, name: String) derives Codec.AsObject
case class CarMotor(name: String) derives Codec.AsObject
case class CarModel(name: String) derives Codec.AsObject
case class StudioImages(url: FullUrl) derives Codec.AsObject
case class CarImages(studio: StudioImages) derives Codec.AsObject
case class CarSpec(battery: String, torque: String, totalKw: String) derives Codec.AsObject
case class CarEnergy(elecRange: String, elecRangeUnit: String) derives Codec.AsObject
case class CarSoftware(version: String, versionTimestamp: String) derives Codec.AsObject
case class CarContent(
  exterior: CarExterior,
  interior: CarInterior,
  wheels: CarWheels,
  motor: CarMotor,
  model: CarModel,
  images: CarImages
) derives Codec.AsObject

case class CarInfo(
  vin: VIN,
  currentPlannedDeliveryDate: Option[LocalDate],
  factoryCompleteDate: Option[LocalDate],
  registrationDate: Option[LocalDate],
  deliveryDate: Option[LocalDate],
  modelYear: String,
  registrationNo: String,
  content: CarContent,
  energy: CarEnergy,
  drivetrain: String,
  software: CarSoftware
) derives Codec.AsObject

case class CarsData(getConsumerCarsV2: Seq[CarInfo]) derives Codec.AsObject

case class CarsResponse(data: CarsData) derives Codec.AsObject

case class Health(
  daysToService: Int,
  brakeFluidLevelWarning: String,
  distanceToServiceKm: Int,
  eventUpdatedTimestamp: Updated
) derives Codec.AsObject

case class Updated(iso: Instant) derives Codec.AsObject

case class Battery(
  batteryChargeLevelPercentage: Int,
  chargerConnectionStatus: String,
  averageEnergyConsumptionKwhPer100Km: Double,
  chargingStatus: String,
  estimatedDistanceToEmptyKm: Int,
  eventUpdatedTimestamp: Updated
) derives Codec.AsObject

case class Odometer(averageSpeedKmPerHour: Int, odometerMeters: Int, eventUpdatedTimestamp: Updated)
  derives Codec.AsObject:
  def odometer: DistanceM = odometerMeters.meters

case class CarTelematics(health: Health, battery: Battery, odometer: Odometer)
  derives Codec.AsObject

case class TelematicsData(carTelematics: CarTelematics) derives Codec.AsObject

case class TelematicsResponse(data: TelematicsData) derives Codec.AsObject
