package com.malliina.boat

object StatsPage {
  def apply(): StatsPage = new StatsPage
}

class StatsPage extends BaseFront {
  document.getElementsByClassName("year-row").foreach { row =>
    row.addOnClick { e =>
      val clickedYear = row.getAttribute("data-year")
      document.getElementsByClassName("month-row").foreach { monthRow =>
        if (monthRow.getAttribute("data-year") == clickedYear) {
          monthRow.toggle()
        }
      }
    }
  }
}
