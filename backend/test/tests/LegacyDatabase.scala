package tests

import com.malliina.boat.db.{BoatSchema, InstantMariaDBProfile}
import org.scalatest.FunSuiteLike

trait LegacyDatabase extends EmbeddedMySQL with AsyncSuite { self: FunSuiteLike =>
  def boatSchema = BoatSchema(ds, InstantMariaDBProfile)
}
