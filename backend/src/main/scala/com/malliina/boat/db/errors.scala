package com.malliina.boat.db

import com.malliina.boat.{BoatName, DeviceId, TrackId, TrackName}
import com.malliina.values.{UserId, Username}

class PrintableException(val message: String) extends Exception(message)

class TrackIdNotFoundException(val id: TrackId)
  extends PrintableException(s"Track ID not found: '$id'.")

class TrackNameNotFoundException(val name: TrackName)
  extends PrintableException(s"Track name not found: '$name'.")

class BoatNotFoundException(val boat: DeviceId, val user: UserId)
  extends PrintableException(s"Boat '$boat' by '$user' not found.")

class BoatNameNotAvailableException(val name: BoatName, val user: Username)
  extends PrintableException(
    s"Boat name '$name' is already taken and therefore not available for '$user'."
  )

class PermissionException(val principal: UserId, val boat: DeviceId, val user: UserId)
  extends PrintableException(
    s"User $principal is not authorized to modify access to boat $boat for user $user."
  )
