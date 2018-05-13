package com.malliina.boat

import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}

object Utils {
  private val generator = new RandomStringGenerator.Builder().withinRange('0', 'z')
    .filteredBy(CharacterPredicates.LETTERS, CharacterPredicates.DIGITS)
    .build()

  def randomString(length: Int) = generator.generate(length)

}
