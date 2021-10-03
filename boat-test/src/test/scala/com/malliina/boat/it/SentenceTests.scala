package com.malliina.boat.it

import com.malliina.boat.*
import com.malliina.boat.db.NewUser
import com.malliina.values.{Password, Username}

import scala.concurrent.{Await, Promise, TimeoutException}

class SentenceTests extends BoatTests:
  // TODO Why is this test ignored?
  http.test("anonymously sent sentence is received by anonymous viewer".ignore) { client =>
    openTestBoat(BoatNames.random(), client) { boat =>
      val sentencePromise = Promise[SentencesEvent]()
      val coordPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(
        Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68"))
      )
      openViewerSocket(client, None) { socket =>
        socket.jsonMessages.map { json =>
          json.as[SentencesEvent].foreach { ss => sentencePromise.trySuccess(ss) }
          json.as[CoordsEvent].foreach { c => coordPromise.trySuccess(c) }
        }.compile.drain.unsafeRunAndForget()
        boat.send(testMessage)
        val received = await(sentencePromise.future)
        assertEquals(received.sentences, testMessage.sentences)
        val coords = await(coordPromise.future).coords
        val expectedCoords = List(Coord.buildOrFail(24.867495, 60.133465))
        assertEquals(coords.map(_.coord), expectedCoords)
      }
    }
  }

  // Ignored because the design is the opposite of the test
  http.test("sent events are not received by unrelated viewer".ignore) { client =>
    val s = server()
    val testUser = Username("User1")
    val testPass = Password("demo")
    s.server.app.userMgmt
      .addUser(NewUser(testUser, None, UserToken.random(), enabled = true))
      .unsafeRunSync()
    val creds = Option(Creds(testUser, testPass))
    openTestBoat(BoatNames.random(), client) { boat =>
      val authPromise = Promise[CoordsEvent]()
      val anonPromise = Promise[CoordsEvent]()
      val testMessage = SentencesMessage(
        Seq(RawSentence("$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68"))
      )
      openViewerSocket(client, None) { anonSocket =>
        anonSocket.jsonMessages.map { json =>
          json.as[CoordsEvent].toOption.filter(_.from.username == testUser).foreach { c =>
            anonPromise.trySuccess(c)
          }
        }.compile.drain.unsafeRunAndForget()
        openViewerSocket(client, creds) { authSocket =>
          authSocket.jsonMessages.map { json =>
            json.as[CoordsEvent].toOption.filter(_.from.username == testUser).foreach { se =>
              authPromise.trySuccess(se)
            }
          }.compile.drain.unsafeRunAndForget()
          boat.send(testMessage)
          await(authPromise.future)
          intercept[TimeoutException] {
            Await.result(anonPromise.future, 500.millis)
          }
        }
      }
    }
  }
