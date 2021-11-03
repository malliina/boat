package com.malliina.boat.db

import com.malliina.boat.{BoatName, DeviceId, TrackId, TrackName}
import com.malliina.values.{UserId, Username}

class TrackIdNotFoundException(val id: TrackId) extends Exception(s"Track ID not found: '$id'.")

class TrackNameNotFoundException(val name: TrackName)
  extends Exception(s"Track name not found: '$name'.")

class BoatNotFoundException(val boat: DeviceId, val user: UserId)
  extends Exception(s"Boat '$boat' by '$user' not found.")

class BoatNameNotAvailableException(val name: BoatName, val user: Username)
  extends Exception(s"Boat name '$name' is already taken and therefore not available for '$user'.")

class PermissionException(val principal: UserId, val boat: DeviceId, val user: UserId)
  extends Exception(
    s"User $principal is not authorized to modify access to boat $boat for user $user."
  )
