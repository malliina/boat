import LatestClient.Files
import com.malliina.http.{FullUrl, OkClient}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object LatestClient {
  case class Files(latest: Option[FullUrl])

  object Files {
    implicit val json: Codec[Files] = deriveCodec[Files]
  }

  def default = new LatestClient(OkClient.default)
}

class LatestClient(http: OkClient) {
  def latest: Option[FullUrl] = Await.result(files, 10.seconds).latest

  def files = http.getAs[Files](FullUrl.https("www.boat-tracker.com", "/files"))
}
