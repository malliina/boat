package com.malliina.polestar.grpc

import cats.effect.std.Dispatcher
import cats.effect.{Async, Resource, Sync}
import cats.implicits.toFlatMapOps
import cats.syntax.all.toShow
import cats.syntax.all.toFunctorOps
import com.malliina.boat.{Battery, ChargerStatus, ChargingStatus, ChargingType, Consumption, Consumptions, Percentage, Updated, VIN, ampere, volts, watts, wh}
import com.malliina.http.{FullUrl, SimpleHttpClient}
import com.malliina.http.UrlSyntax.https
import com.malliina.measure.DistanceIntM
import com.malliina.util.AppLogger
import com.malliina.values.{AccessToken, ErrorMessage, error}
import fs2.grpc.client.{ClientAspect, ClientCallContext, ClientOptions, Fs2ClientCall}
import fs2.grpc.syntax.all.*
import io.grpc.MethodDescriptor.MethodType
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.{ManagedChannel, Metadata, MethodDescriptor}
import pccs.chronos.messages.common.v1.chronos_request.ChronosRequest
import pccs.chronos.messages.targetsoc.v1.target_soc.{GetTargetSocRequest, GetTargetSocResponse}
import pccs.chronos.services.v1.target_soc_service.TargetSocServiceFs2Grpc
import pccs.vehiclestates.services.battery.v1.battery_service.{BatteryServiceFs2Grpc, BatteryServiceProto, GetBatteryRequest, GetBatteryResponse}
import scalapb.grpc.{ConcreteProtoMethodDescriptorSupplier, Marshaller}

import java.util.UUID
import scala.concurrent.duration.DurationInt

object GRPCClient:
  val log = AppLogger(getClass)

class GRPCClient[F[_]: {Async, Sync}](http: SimpleHttpClient[F]):
  val discoveryUrl: FullUrl = https"cnepmob.volvocars.com"
  val pcssHost = "api.pccs-prod.plstr.io"

  def battery(vin: VIN, token: AccessToken): F[Either[ErrorMessage, Battery]] =
    fetchBattery(vin, token).map: res =>
      for
        b <- res.battery.toRight(s"No battery information for $vin.".error)
        chargeLevel <- Percentage.build(b.batteryChargeLevelPercentage)
        updated <- b.timestamp
          .map(t => Updated(s"${t.seconds}", t.nanos.toLong))
          .toRight(s"No timestamp for battery information for $vin.".error)
      yield { 
        Battery(
          vin,
          chargeLevel,
          ChargerStatus.fromPolestar(b.chargerConnectionStatus.name),
          ChargingStatus.fromPolestar(b.chargingStatus.name),
          Option.when(b.chargingPowerWatts > 0)(b.chargingPowerWatts.toDouble.watts),
          Option.when(b.chargingCurrentAmps > 0)(b.chargingCurrentAmps.toDouble.ampere),
          Option.when(b.chargingVoltageVolts > 0)(b.chargingVoltageVolts.toDouble.volts),
          ChargingType.fromPolestar(b.chargingType.name),
          Consumptions(
            Consumption(b.totalEnergyConsumptionWh.wh, b.averageEnergyConsumptionKwhPer100Km),
            Consumption(
              b.totalEnergyConsumptionWhAutomatic.wh,
              b.averageEnergyConsumptionKwhPer100KmAutomatic
            ),
            Consumption(
              b.totalEnergyConsumptionWhSinceCharge.wh,
              b.averageEnergyConsumptionKwhPer100KmSinceCharge
            )
          ),
          Option.when(b.estimatedChargingTimeToFullMinutes > 0)(
            b.estimatedChargingTimeToFullMinutes.minutes
          ),
          b.estimatedDistanceToEmptyKm.kilometers,
          updated
        )
      }

  def fetchBattery(vin: VIN, token: AccessToken): F[GetBatteryResponse] =
    discover.flatMap: discovered =>
      channel(discovered.c3.grpcHost, discovered.c3.grpcPort)
        .flatMap: ch =>
          Dispatcher
            .parallel[F]
            .map: d =>
              new BatteryServiceFs2Grpc[F, Metadata]:
                override def getBattery(request: GetBatteryRequest, ctx: Metadata)
                  : fs2.Stream[F, GetBatteryResponse] = fs2.Stream.empty

                override def getLatestBattery(request: GetBatteryRequest, ctx: Metadata)
                  : F[GetBatteryResponse] =
                  val aspect =
                    (new ClientAspect.Default[F] {}).contraModify((m: Metadata) => Sync[F].pure(m))
                  val batteryMethod = MethodDescriptor
                    .newBuilder()
                    .setType(MethodType.UNARY)
                    .setFullMethodName(
                      "services.vehiclestates.battery.BatteryService/GetLatestBattery"
                    )
                    .setSampledToLocalTracing(false)
                    .setRequestMarshaller(Marshaller.forMessage[GetBatteryRequest])
                    .setResponseMarshaller(Marshaller.forMessage[GetBatteryResponse])
                    .setSchemaDescriptor(
                      ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(
                        BatteryServiceProto.javaDescriptor.getServices.get(0).getMethods.get(1)
                      )
                    )
                    .build()
                  aspect.visitUnaryToUnaryCall[GetBatteryRequest, GetBatteryResponse](
                    ClientCallContext(ctx, batteryMethod),
                    request,
                    (req, m) =>
                      Fs2ClientCall[F](ch, batteryMethod, d, ClientOptions.default)
                        .flatMap(_.unaryToUnaryCall(req, m))
                  )
        .use: s =>
          val req = GetBatteryRequest.of(UUID.randomUUID().toString, vin.show)
          s.getLatestBattery(req, metadata(vin, token))

  def targetSoc(vin: VIN, token: AccessToken): F[GetTargetSocResponse] =
    channel(pcssHost, 443)
      .flatMap: ch =>
        TargetSocServiceFs2Grpc.stubResource[F](ch)
      .use: s =>
        val cr = ChronosRequest.of(UUID.randomUUID().toString, vin.show, "mobile", None)
        val req = GetTargetSocRequest.of(Option(cr))
        s.getTargetSoc(req, metadata(vin, token))
          .take(1)
          .compile
          .toList
          .map(_.head)

  def discover =
    http.getAs[Discovery](discoveryUrl, Map("Accept" -> "application/volvo.cloud.cnepmob.v1+json"))

  private def channel(host: String, port: Int): Resource[F, ManagedChannel] =
    NettyChannelBuilder.forAddress(host, port).resource[F]

  private def metadata(vin: VIN, token: AccessToken): Metadata =
    val meta = new Metadata()
    meta.put(metaKey("authorization"), s"Bearer $token")
    meta.put(metaKey("vin"), vin.show)
    meta

  private def metaKey(key: String) =
    Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)
