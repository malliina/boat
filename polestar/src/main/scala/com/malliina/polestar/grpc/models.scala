package com.malliina.polestar.grpc

import com.malliina.http.FullUrl
import io.circe.Codec

case class C3Discovery(url: FullUrl, grpcHost: String, grpcPort: Int) derives Codec.AsObject

case class Discovery(c3: C3Discovery) derives Codec.AsObject
