package com.malliina.boat

import play.api.libs.json.Json

class BoatTests extends munit.FunSuite with DemoJson {
  test("serialize GeoJSON") {
    val geo = LineGeometry("LineString", Seq(Coord.buildOrFail(24, 60)))
    assert(1 == 1)
  }

  test("parse mark") {
    val json = Json.parse(markJson)
    val mark = MarineSymbol.reader.reads(json)
    assert(mark.isSuccess)
    assert(mark.get.nameFi.contains("Saukonlaituri 2"))
  }

  test("parse limit") {
    val limit = LimitArea.reader.reads(Json.parse(limitAreaJson))
    assert(limit.isSuccess)
    val l = limit.get
    assert(l.limit.exists(s => s.toKmh > 9 && s.toKmh < 11))
    assert(l.length.exists(len => len.meters > 675 && len.meters < 677))
  }
}

trait DemoJson {
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
}
