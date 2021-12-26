package com.malliina.boat.auth

import com.malliina.http.io.HttpClientIO
import com.malliina.values.IdToken
import com.malliina.web.{ClientId, GoogleAuthFlow, AppleTokenValidator}
import tests.BaseSuite
import java.time.Instant
import scala.jdk.CollectionConverters.MapHasAsScala

class TokenTests extends BaseSuite:
  test("google token validation".ignore) {
    val in = "token_here"
    val client = GoogleAuthFlow.keyClient(Seq(ClientId("client_id")), HttpClientIO())
    val outcome = client.validate(IdToken(in)).unsafeRunSync()
    assert(outcome.isRight)
    val v = outcome.toOption.get
    v.parsed.claims.getClaims.asScala.foreach { case (k, value) =>
      println(s"$k=$value")
    }
  }

  test("iOS SIWA token".ignore) {
    val token = IdToken(
      "eyJraWQiOiJlWGF1bm1MIiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29tIiwiYXVkIjoiY29tLm1hbGxpaW5hLkJvYXRUcmFja2VyIiwiZXhwIjoxNjQwNTk4ODAwLCJpYXQiOjE2NDA1MTI0MDAsInN1YiI6IjAwMTYzMy5lZTIxNjZhNzQ2ZjU0ODJkYTU5MGNlZGI2Nzg3YjM0My4xNzE5IiwiY19oYXNoIjoiajJIZlpaR3RTangtMFY1d1FyNUVtdyIsImVtYWlsIjoibWFsbGlpbmExMjNAZ21haWwuY29tIiwiZW1haWxfdmVyaWZpZWQiOiJ0cnVlIiwiYXV0aF90aW1lIjoxNjQwNTEyNDAwLCJub25jZV9zdXBwb3J0ZWQiOnRydWV9.G8gEA3nOw7wvT3tVEBj51cJ4xdJrN7aRUnu9FFS75wnql6YY_HoQsNa4WWiElFDmAXfUZiqC5Ap_MMgGUuICzcrow7EhkRtX0rLsXSYypAgcRA7Y-K8Jp0MeEAeeknlJTrgXsPYdg16yR4Zhxe2Cne3V7R0ng_sutxUepZ7KR4JZU81z8dTvjjaNuogQzk5liFuvFcW8TeWElOciIwrwqTTUrfngUG526vCkBBYgoC1ZGuId3XGgTDW3YvuPcbb6ZujqjCE4lR2YIguhrn4SJP62hLwo58KWI7xmHyZ81WsnaycRKz7t_H63K7MvQHbQvuRXvreZXleWeW-lleIyQw"
    )
    val v = AppleTokenValidator.app(HttpClientIO())
    val res = v.validateToken(token, Instant.now()).unsafeRunSync()
  }
