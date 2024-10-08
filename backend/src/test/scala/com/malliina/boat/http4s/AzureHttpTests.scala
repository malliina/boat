package com.malliina.boat.http4s

import com.malliina.boat.{DeviceId, ErrorConstants, Latitude, LocationUpdate, LocationUpdates, Longitude, MUnitSuite, TestEmailAuth}
import com.malliina.http.io.HttpClientIO
import com.malliina.http.{Errors, FullUrl}
import com.malliina.measure.DistanceIntM
import com.malliina.values.{IdToken, degrees}
import io.circe.syntax.EncoderOps
import okhttp3.{Interceptor, OkHttpClient}
import org.http4s.Status.Unauthorized

import java.time.{OffsetDateTime, ZoneOffset}

class AzureHttpTests extends MUnitSuite:
  val loc = LocationUpdate(
    Longitude.unsafe(24),
    Latitude.unsafe(60),
    Option(1.meters),
    Option(5.meters),
    Option(128f.degrees),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    OffsetDateTime.of(2023, 4, 2, 10, 4, 3, 0, ZoneOffset.UTC)
  )

  test("POST car locations with outdated jwt returns 401 with token expired prod".ignore):
    val b = new OkHttpClient.Builder()
      .addNetworkInterceptor(LoggingInterceptor())
      .build()
    val http = HttpClientIO(b)
    val expired = IdToken("changeme")
    http
      .postJson(
        url = FullUrl.https("www.boat-tracker.com", "/cars/locations"),
        json = LocationUpdates(List(loc), DeviceId(1234)).asJson,
        headers = headers(expired) ++ Map("Accept-Encoding" -> "identity")
      )
      .map: res =>
        assertEquals(res.status, Unauthorized.code)
        assert(
          res
            .parse[Errors]
            .toOption
            .exists(_.errors.exists(_.key == ErrorConstants.TokenExpiredKey))
        )

  private def headers(token: IdToken = TestEmailAuth.testToken) = Map(
    "Authorization" -> s"Bearer $token",
    "Accept" -> "application/json"
  )

class LoggingInterceptor extends Interceptor:
  def intercept(chain: Interceptor.Chain) =
    val request = chain.request
    println(s"Sending ${request.method()} ${request.url()}\n${request.headers()}")
    val response = chain.proceed(request)

    println(
      s"Received response ${response.code()} for ${response.request().url()} with \n${response
          .headers()}\nbody was\n${response.body().string()}"
    )
    response
