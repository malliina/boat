package com.malliina.polestar.grpc

import cats.effect.IO
import ch.qos.logback.classic.Level
import com.malliina.http.HttpClient
import com.malliina.logback.LogbackUtils
import com.malliina.polestar.{Polestar, PolestarConfig}
import com.malliina.values.AccessToken

class GRPCTests extends munit.CatsEffectSuite:
  LogbackUtils.init(rootLevel = Level.INFO)

  val creds = PolestarConfig.conf

  val http = ResourceFunFixture(HttpClient.resource[IO]())
  val polestar = ResourceFunFixture(Polestar.resource[IO])

  polestar.test("Discover C3 host".ignore): client =>
    val grpc = GRPCClient(client.http)
    grpc.discover.map: json =>
      println(json)
      assertEquals(1, 1)

  polestar.test("Fetch battery using gRPC".ignore): client =>
    val battery = client.auth
      .fetchTokens(creds)
      .flatMap: tokens =>
        fetchBattery(tokens.accessToken, client)
    battery.map: b =>
      println(b)

  polestar.test("Fetch target soc using gRPC".ignore): client =>
    val soc = client.auth
      .fetchTokens(creds)
      .flatMap: tokens =>
        fetchTargetSoc(tokens.accessToken, client)
    soc.map: b =>
      println(b)

  private def fetchBattery(token: AccessToken, client: Polestar[IO]) =
    val grpc = GRPCClient(client.http)
    client
      .fetchCars(token)
      .flatMap: cars =>
        val car = cars.head
        grpc.fetchBattery(car.vin, token)

  private def fetchTargetSoc(token: AccessToken, client: Polestar[IO]) =
    val grpc = GRPCClient(client.http)
    client
      .fetchCars(token)
      .flatMap: cars =>
        val car = cars.head
        grpc.targetSoc(car.vin, token)
