package com.malliina.boat.parsing

import com.malliina.boat.RawSentence

trait NMEA0183Parser {
  def parse(raw: RawSentence): Either[InvalidSentence, TalkedSentence]
}
