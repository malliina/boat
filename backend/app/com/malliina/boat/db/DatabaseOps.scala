package com.malliina.boat.db

import scala.concurrent.Future

class DatabaseOps(db: BoatSchema) {

  import db.api._

  protected def action[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)
}