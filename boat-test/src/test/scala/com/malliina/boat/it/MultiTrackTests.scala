package com.malliina.boat.it

import com.malliina.boat.{BoatNames, RawSentence, SentencesMessage}
import com.malliina.http.FullUrl
import tests.BaseSuite

class MultiTrackTests extends BaseSuite with BoatSockets {
  //  def url = FullUrl.ws("localhost:9000", reverse.boats().toString)
  def url = FullUrl.wss("boat.malliina.com", reverse.boats().toString)

  val track1 = Seq(
    "$GPVTG,51.0,T,42.2,M,2.4,N,4.4,K,A*25",
    "$SDMTW,15.2,C*02",
    "$GPZDA,141735,04,05,2018,-03,00*69",
    "$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68",
    "$GPGGA,154817,6009.8242,N,02450.8647,E,1,12,0.60,-2,M,19.5,M,,*48"
  )

  val track2 = Seq(
    "$GPVTG,51.0,T,42.2,M,2.4,N,4.4,K,A*25",
    "$SDMTW,15.2,C*02",
    "$GPZDA,141735,04,05,2018,-03,00*69",
    "$GPGGA,162112,6009.0969,N,02453.4521,E,1,12,0.70,6,M,19.5,M,,*6F",
    "$GPGGA,174239,6010.2076,N,02450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
    "$GPGGA,124943,6009.5444,N,02448.4491,E,1,12,0.60,0,M,19.5,M,,*61",
    "$GPGGA,125642,6009.2559,N,02447.5942,E,1,12,0.60,1,M,19.5,M,,*68"
  )

  def msg(ts: Seq[String]) = SentencesMessage(ts.map(RawSentence.apply))

  ignore("two tracks") {
    openBoat(url, BoatNames.random()) { boat1 =>
      boat1.send(msg(track1))
      openBoat(url, BoatNames.random()) { boat2 =>
        boat2.send(msg(track2))
      }
    }
  }
}
