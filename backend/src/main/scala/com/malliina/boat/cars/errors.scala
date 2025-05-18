package com.malliina.boat.cars

import com.malliina.values.UserId

abstract class PolestarException(message: String, cause: Option[Exception])
  extends Exception(message, cause.orNull)

class NoTokens(owner: UserId) extends PolestarException(s"No tokens for '$owner'.", None)

class RefreshFailed(owner: UserId, cause: Exception)
  extends PolestarException(s"Failed to refresh tokens for '$owner'.", Option(cause))
