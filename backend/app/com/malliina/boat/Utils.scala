package com.malliina.boat

import org.apache.commons.text.{CharacterPredicates, RandomStringGenerator}

object Utils {
  private val generator = new RandomStringGenerator.Builder().withinRange('a', 'z')
    .filteredBy(CharacterPredicates.LETTERS)
    .build()

  def randomString(length: Int) = generator.generate(length).toLowerCase
}
