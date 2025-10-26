package com.malliina.polestar

import com.malliina.boat.{CarsTelematics, RegistrationNumber, VIN}
import com.malliina.http.FullUrl
import io.circe.Codec

import java.time.LocalDate

case class OpenIdConfiguration(authorization_endpoint: FullUrl, token_endpoint: FullUrl)
  derives Codec.AsObject:
  def authorizationEndpoint = authorization_endpoint
  def tokenEndpoint = token_endpoint

case class VINSVariable(vins: Seq[VIN]) derives Codec.AsObject
case class GraphQuery(query: String, variables: Option[VINSVariable]) derives Codec.AsObject
object GraphQuery:
  def vin(query: String, vin: VIN) = apply(query, Option(VINSVariable(Seq(vin))))

/** @param code
  *   "72300"
  * @param name
  *   "Midnight"
  */
case class CarExterior(code: String, name: String) derives Codec.AsObject

/** @param code
  *   "RF8000"
  * @param name
  *   "WeaveTech in Charcoal with Black ash deco"
  */
case class CarInterior(code: String, name: String) derives Codec.AsObject
case class CarWheels(code: String, name: String) derives Codec.AsObject
case class CarMotor(name: String) derives Codec.AsObject
case class CarModel(name: String) derives Codec.AsObject
case class StudioImages(url: FullUrl) derives Codec.AsObject
case class CarImages(studio: StudioImages) derives Codec.AsObject

/** @param battery
  *   "78 kWh"
  * @param torque
  *   "487 lb-ft"
  * @param totalKw
  *   "300 kW"
  */
case class CarSpec(battery: String, torque: String, totalKw: String) derives Codec.AsObject
case class CarEnergy(elecRange: String, elecRangeUnit: String) derives Codec.AsObject

/** @param version
  *   "P03.04"
  * @param versionTimestamp
  *   "2025-03-22 14:52:43"
  */
case class CarSoftware(version: String, versionTimestamp: String) derives Codec.AsObject
case class CarContent(
  exterior: CarExterior,
  interior: CarInterior,
  wheels: CarWheels,
  motor: CarMotor,
  model: CarModel
  // images: CarImages
) derives Codec.AsObject

case class PolestarCarInfo(
  vin: VIN,
  currentPlannedDeliveryDate: Option[LocalDate],
  factoryCompleteDate: Option[LocalDate],
  registrationDate: Option[LocalDate],
  deliveryDate: Option[LocalDate],
  modelYear: String, // "2023"
  registrationNo: RegistrationNumber,
  content: CarContent,
  energy: CarEnergy,
  drivetrain: String,
  software: CarSoftware
) derives Codec.AsObject

case class CarsData(getConsumerCarsV2: Seq[PolestarCarInfo]) derives Codec.AsObject

case class CarsResponse(data: CarsData) derives Codec.AsObject

case class TelematicsData(carTelematicsV2: CarsTelematics) derives Codec.AsObject

case class TelematicsResponse(data: TelematicsData) derives Codec.AsObject

class PolestarAuthException(message: String, cause: Option[Exception])
  extends Exception(message, cause.orNull)
