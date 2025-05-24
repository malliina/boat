package com.malliina.boat.cars

import com.malliina.boat.VIN
import com.malliina.values.{ErrorMessage, UserId}

class PolestarException(message: ErrorMessage, cause: Option[Exception])
  extends Exception(message.message, cause.orNull)

class NoTokens(owner: UserId)
  extends PolestarException(ErrorMessage(s"No tokens for '$owner'."), None)

class RefreshFailed(owner: UserId, cause: Exception)
  extends PolestarException(ErrorMessage(s"Failed to refresh tokens for '$owner'."), Option(cause))

class CarException(message: ErrorMessage, val vin: VIN, cause: Option[Exception])
  extends Exception(message.message, cause.orNull)
