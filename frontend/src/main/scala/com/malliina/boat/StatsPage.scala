package com.malliina.boat

class StatsPage extends BaseFront:
  document
    .getElementsByClassName("year-row")
    .foreach: row =>
      row.addOnClick: e =>
        val clickedYear = row.getAttribute("data-year")
        document
          .getElementsByClassName("month-row")
          .foreach: monthRow =>
            if monthRow.getAttribute("data-year") == clickedYear then monthRow.toggle()
