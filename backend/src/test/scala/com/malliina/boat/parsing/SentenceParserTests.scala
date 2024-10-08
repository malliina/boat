package com.malliina.boat.parsing

import com.malliina.boat.RawSentence
import com.malliina.measure.{LatitudeDM, LongitudeDM}

class SentenceParserTests extends munit.FunSuite:
  val track = Seq(
    "$SDDPT,23.9,0.0,*43",
    "$GPVTG,51.0,T,42.2,M,2.4,N,4.4,K,A*25",
    "$SDMTW,15.2,C*02",
    "$GPZDA,141735,04,05,2018,-03,00*69",
    "$GPGGA,154106,6008.0079,N,02452.0497,E,1,12,0.60,0,M,19.5,M,,*68",
    "$GPGGA,154817,6009.8242,N,02450.8647,E,1,12,0.60,-2,M,19.5,M,,*48"
  ).map(RawSentence.apply)

  test("parse sentences"):
    val ok = track
      .map(SentenceParser.parse)
      .collect:
        case Right(sentence) =>
          sentence

    val strs = ok.map:
      case DPTMessage(_, depth, _)               => s"GPT $depth"
      case VTGMessage(_, _, _, speed, _)         => s"VTG $speed"
      case MTWMessage(_, temp)                   => s"MTW $temp"
      case zda @ ZDAMessage(_, _, _, _, _, _, _) => s"ZDA ${zda.dateTimeUtc}"
      case GGAMessage(_, _, lat, lng, _, _, _, _, _, _) =>
        s"GGA ${lat.toDecimalDegrees} ${lng.toDecimalDegrees}"
      case _ => ""
    assert(strs.length == track.length)

  test("read and convert GGA coordinates from degrees minutes to decimal degrees"):
    val dmLatResult = LatitudeDM.parse("6009.1905,N")
    assert(dmLatResult.isRight)
    val dmLat = dmLatResult.toOption.get
    val dLat = dmLat.minutes / 60
    val actualLat = dmLat.degrees + dLat
    assert(actualLat.toString.take(9) == "60.153175")

    val dmLng = LongitudeDM.parse("02453.4979,E").toOption.get
    val dLng = dmLng.minutes / 60
    val actualLng = dmLng.degrees + dLng
    assert(actualLng.toString.take(9) == "24.891631")

  test("parse RMC"):
    val in = RawSentence("$GPRMC,205152.000,A,6009.1925,N,02453.2406,E,0.00,353.55,070919,,,D*69")
    val rmc = SentenceParser
      .parse(in)
      .toSeq
      .collectFirst:
        case msg @ RMCMessage(_, _, _, _, _) =>
          msg.dateTimeUtc
      .get
    assert(rmc.getHour == 20)
