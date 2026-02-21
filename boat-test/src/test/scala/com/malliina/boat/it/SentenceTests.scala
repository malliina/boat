package com.malliina.boat.it

import cats.effect.{Deferred, IO}
import com.malliina.boat.*
import com.malliina.boat.db.NewUser
import com.malliina.geo.Coord
import com.malliina.values.{Password, Username}

import scala.concurrent.TimeoutException

class SentenceTests extends BoatTests:
  // TODO Why is this test ignored?
  http.test("anonymously sent sentence is received by anonymous viewer".ignore): client =>
    openTestBoat(BoatNames.random(), client): boat =>
      Deferred[IO, SentencesEvent].flatMap: sentencePromise =>
        Deferred[IO, CoordsEvent].flatMap: coordPromise =>
          val testMessage = SentencesMessage(
            Seq(
              RawSentence.unsafe("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")
            )
          )
          val viewer = openViewerSocket(client, None): socket =>
            socket.jsonMessages
              .evalTap: json =>
                json
                  .as[SentencesEvent]
                  .map(ss => sentencePromise.complete(ss))
                  .getOrElse(IO.unit) >>
                  json.as[CoordsEvent].map(c => coordPromise.complete(c)).getOrElse(IO.unit)
              .compile
              .drain
          val messages = boat
            .send(testMessage)
            .map: _ =>
              sentencePromise.get.flatMap: received =>
                assertEquals(received.sentences, testMessage.sentences)
                coordPromise.get.map: coords =>
                  val expectedCoords = List(Coord.buildOrFail(24.867495, 60.133465))
                  assertEquals(coords.coords.map(_.coord), expectedCoords)
          for
            _ <- viewer.start
            _ <- messages
          yield messages

  // Ignored because the design is the opposite of the test
  http.test("sent events are not received by unrelated viewer".ignore): client =>
    val s = server()
    val testUser = Username.unsafe("User1")
    val testPass = Password.unsafe("demo")
    s.server.app.userMgmt
      .addUser(NewUser(testUser, None, UserToken.random(), enabled = true))
      .map: _ =>
        val creds = Option(Creds(testUser, testPass))
        openTestBoat(BoatNames.random(), client): boat =>
          Deferred[IO, CoordsEvent].flatMap: authPromise =>
            Deferred[IO, CoordsEvent].flatMap: anonPromise =>
              val testMessage = SentencesMessage(
                Seq(
                  RawSentence
                    .unsafe("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68")
                )
              )
              val anonSocket = openViewerSocket(client, None): anonSocket =>
                anonSocket.jsonMessages
                  .evalTap: json =>
                    json
                      .as[CoordsEvent]
                      .toOption
                      .filter(_.from.username == testUser)
                      .map(c => anonPromise.complete(c))
                      .getOrElse(IO.unit)
                  .compile
                  .drain
              val authedSocket = openViewerSocket(client, creds): authSocket =>
                authSocket.jsonMessages
                  .evalTap: json =>
                    json
                      .as[CoordsEvent]
                      .toOption
                      .filter(_.from.username == testUser)
                      .map(se => authPromise.complete(se))
                      .getOrElse(IO.unit)
                  .compile
                  .drain
              val process = boat
                .send(testMessage)
                .flatMap: _ =>
                  authPromise.get.flatMap: _ =>
                    interceptIO[TimeoutException]:
                      anonPromise.get.timeout(500.millis)
              for
                _ <- anonSocket.start
                _ <- authedSocket.start
                res <- process
              yield res
