package com.malliina.boat.db

import cats.implicits._
import com.malliina.boat.{FairwayInfo, FeatureCollection}
import doobie.implicits._
import io.circe.parser.decode
import tests.{MUnitDatabaseSuite, MUnitSuite}

import java.nio.file.Files

class FairwayDatabaseTests extends MUnitSuite with MUnitDatabaseSuite {
  dbFixture.test("import fairways to database".ignore) { database =>
    val fileIn = userHome.resolve(".boat/vaylat/vaylat-geo.json")
    val strIn = Files.readString(fileIn)
    val coll = decode[FeatureCollection](strIn).toOption.get
    val fs = coll.features
    val svc = DoobieFairwayService(database)

    val insertTask = fs.toList.traverse { f =>
      def coords(id: FairwayId) = f.geometry.coords.map { coord =>
        FairwayCoordInput(coord, coord.lat, coord.lng, coord.hash, id)
      }.toList
      val in = f.props.as[FairwayInfo].toOption.get
      for {
        id <- svc.insert(in)
        cids <- svc.insertCoords(coords(id))
      } yield cids
    }
    val inserts = for {
      _ <- svc.delete
      cids <- insertTask
    } yield cids
    database.run(inserts).unsafeRunSync()
  }
}
