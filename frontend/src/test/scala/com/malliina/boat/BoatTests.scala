package com.malliina.boat

import io.circe.parser.decode

class BoatTests extends munit.FunSuite with DemoJson:
  test("hej"):
    val in = List(6, 5, 4, 3, 15, 14, 13, 13, 13, 16, 15, 6)
    val expected = 1 + 1 + 1 + 1 + 1 + 1 + 9
    val decrements = in
      .sliding(2)
      .collect:
        case Seq(a, b) if b < a => b - a
    assertEquals(decrements.sum.abs, expected)

  test("serialize GeoJSON"):
    val _ = LineGeometry("LineString", Seq(Coord.buildOrFail(24, 60)))
    assertEquals(1, 1)

  test("parse mark"):
    val mark = decode[MarineSymbol](markJson)
    assert(mark.isRight)
    assert(mark.toOption.get.nameFi.contains("Saukonlaituri 2"))

  test("parse limit"):
    val limit = decode[LimitArea](limitAreaJson)
    assert(limit.isRight)
    val l = limit.toOption.get
    assert(l.limit.exists(s => s.toKmh > 9 && s.toKmh < 11))
    assert(l.length.exists(len => len.meters > 675 && len.meters < 677))

trait DemoJson:
  val markJson =
    """{
     |    "NAVL_TYYP": 4,
     |    "TILA": "VAHVISTETTU",
     |    "IRROTUS_PV": "2020-03-30T02:14:28",
     |    "TUTKAHEIJ": 0,
     |    "TLNUMERO": 79590,
     |    "OMISTAJA": "Helsingin kaupunki",
     |    "SIJAINTIS": "Saukonlaiturin ja Saukonpaaden välisessä kanavassa, asennettu merkitsemään vesisyvyyksien rajaa.",
     |    "HUIPPUMERK": 0,
     |    "VAYLAT": "4720",
     |    "TOTI_TYYP": 1,
     |    "PATA_TYYP": 63,
     |    "FASADIVALO": 0,
     |    "NIMIS": "Saukonlaituri 2",
     |    "TKLNUMERO": 56,
     |    "VALAISTU": "E",
     |    "SUBTYPE": "KELLUVA",
     |    "TY_JNR": 10,
     |    "PAKO_TYYP": 5,
     |    "PAIV_PVM": "2015-09-27T21:00:00.000+0000",
     |    "VAHV_PVM": "2015-09-27T21:00:00.000+0000"
     |
     |}""".stripMargin

  val limitAreaJson =
    """{
      |    "IRROTUS_PV": "2020-03-30T02:11:45",
      |    "RAJOITUSTY": "01, 02",
      |    "NIMI_SIJAI": "Kaskisaarensalmi",
      |    "OBJECTID": 103968,
      |    "JNRO": "4594",
      |    "PITUUS": 676,
      |    "SUURUUS": 10
      |}""".stripMargin
