package com.malliina.boat

import cats.Show
import com.malliina.values.{ErrorMessage, JsonCompanion}
import com.malliina.values.Literals.err
import org.typelevel.ci.CIString

opaque type RegistrationNumber = CIString
object RegistrationNumber extends CICompanion[RegistrationNumber]:
  val Key = "registrationNumber"
  override def apply(raw: CIString): RegistrationNumber = raw
  override def write(t: RegistrationNumber): CIString = t

opaque type VIN = CIString
object VIN extends CICompanion[VIN]:
  override def apply(str: CIString): VIN = str

  override def build(input: CIString): Either[ErrorMessage, VIN] =
    if input.toString.isBlank then Left(err"VIN must not be blank.")
    else Right(input.trim)

  override def write(t: VIN): CIString = t

abstract class CICompanion[T] extends JsonCompanion[CIString, T]:
  given Show[T] = Show(t => write(t).toString)
