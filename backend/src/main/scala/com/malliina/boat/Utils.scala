package com.malliina.boat

import cats.Show
import cats.syntax.show.toShow
import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}

import java.text.Normalizer
import scala.concurrent.duration.{DurationLong, FiniteDuration}

object Utils:
  private val generator: RandomStringGenerator =
    new RandomStringGenerator.Builder()
      .withinRange('a', 'z')
      .filteredBy(CharacterPredicates.LETTERS)
      .get()

  def randomString(length: Int) = generator.generate(length).toLowerCase

  def normalize[T: Show](input: T): String = normalize(input.show)

  def normalize(input: String): String =
    Normalizer
      .normalize(input, Normalizer.Form.NFD)
      .replaceAll("[^\\p{ASCII}]", "")
      .toLowerCase
      .replaceAll("[^-a-zA-Z0-9]", "-")
      .trim

  def timed[T](code: => T): (T, FiniteDuration) =
    val start = System.currentTimeMillis()
    val t = code
    val duration = (System.currentTimeMillis() - start).millis
    (t, duration)
