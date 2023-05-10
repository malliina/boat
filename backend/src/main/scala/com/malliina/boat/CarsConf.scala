package com.malliina.boat

import io.circe.Codec

case class CarProfileLang(
  signedInAs: String,
  driving: String,
  chooseLanguage: String,
  signInWith: String,
  signOut: String
) derives Codec.AsObject
case class CarStatsLang(speed: String, height: String, nightMode: String, dayMode: String)
  derives Codec.AsObject
case class CarSettingsLang(selectCar: String) derives Codec.AsObject
case class CarLang(
  appName: String,
  language: Language,
  profile: CarProfileLang,
  settings: CarSettingsLang,
  stats: CarStatsLang
) derives Codec.AsObject
case class CarLanguages(finnish: CarLang, swedish: CarLang, english: CarLang) derives Codec.AsObject
object CarLanguages:
  val default = CarLanguages(
    CarLang(
      "Car-Tracker",
      Language.finnish,
      CarProfileLang(
        "Käyttäjätunnus",
        "Ajat autoa",
        "Valitse kieli",
        "Kirjaudu palvelulla",
        "Kirjaudu ulos"
      ),
      CarSettingsLang("Valitse auto"),
      CarStatsLang("Nopeus", "Korkeus", "Yötila", "Päivätila")
    ),
    CarLang(
      "Car-Tracker",
      Language.swedish,
      CarProfileLang("Inloggad som", "Du kör", "Välj språk", "Logga in med", "Logga ut"),
      CarSettingsLang("Välj bil"),
      CarStatsLang("Hastighet", "Höjd", "Nattläge", "Dagläge")
    ),
    CarLang(
      "Car-Tracker",
      Language.english,
      CarProfileLang("Signed in as", "Driving", "Select language", "Sign in with", "Sign out"),
      CarSettingsLang("Select car"),
      CarStatsLang("Speed", "Height", "Night mode", "Day mode")
    )
  )

case class CarsConf(languages: CarLanguages) derives Codec.AsObject
object CarsConf:
  val default = CarsConf(CarLanguages.default)
