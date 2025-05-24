package com.malliina.boat

class StatsPage extends BaseFront:
  document
    .getElementsByClassName(YearRow)
    .foreach: row =>
      row.addOnClick: _ =>
        val clickedYear = row.getAttribute(DataYear)
        document
          .getElementsByClassName(MonthRow)
          .foreach: monthRow =>
            if monthRow.getAttribute(DataYear) == clickedYear then monthRow.toggle()
