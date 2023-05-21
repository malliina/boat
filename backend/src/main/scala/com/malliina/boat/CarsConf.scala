package com.malliina.boat

import cats.data.NonEmptyList
import io.circe.Codec

case class CarProfileLang(
  signedInAs: String,
  driving: String,
  chooseLanguage: String,
  signInWith: String,
  signOut: String,
  failedToLoadProfile: String,
  failedToSignIn: String,
  goToMap: String,
  version: String,
  nothingHere: String
) derives Codec.AsObject
case class CarStatsLang(
  speed: String,
  altitude: String,
  nightMode: String,
  dayMode: String,
  bearing: String,
  accuracy: String,
  degrees: String,
  meters: String,
  batteryLevel: String,
  capacity: String,
  range: String,
  outsideTemperature: String
) derives Codec.AsObject
case class PermissionsLang(
  grantCta: String,
  grantAccess: String,
  explanation: String,
  tryAgain: String,
  openSettingsText: String
) derives Codec.AsObject
case class CarSettingsLang(title: String, openSettings: String, selectCar: String, noCars: String)
  derives Codec.AsObject
case class CarLanguage(code: Language, name: String) derives Codec.AsObject
case class CarLang(
  appName: String,
  language: CarLanguage,
  profile: CarProfileLang,
  settings: CarSettingsLang,
  permissions: PermissionsLang,
  stats: CarStatsLang
) derives Codec.AsObject
object CarLanguages:
  val default = NonEmptyList.of(
    CarLang(
      "Car-Tracker",
      CarLanguage(Language.english, "English"),
      CarProfileLang(
        "Signed in as",
        "Driving",
        "Select language",
        "Sign in with",
        "Sign out",
        "Failed to load profile.",
        "Failed to sign in.",
        "Go to map",
        "Version",
        "Nothing to see here."
      ),
      CarSettingsLang("Settings", "Open Settings", "Select car", "No cars."),
      PermissionsLang(
        "Grant permissions",
        "Grant app access to location and car",
        "This app needs access to location and car properties in order to store them to your Car-Tracker account.",
        "Try again",
        "Please open Settings and grant app-level permissions for this app."
      ),
      CarStatsLang(
        "Speed",
        "Altitude",
        "Night mode",
        "Day mode",
        "Bearing",
        "Accuracy",
        "degrees",
        "meters",
        "Battery level",
        "Capacity",
        "Range",
        "Temperature outside"
      )
    ),
    CarLang(
      "Car-Tracker",
      CarLanguage(Language.swedish, "Svenska"),
      CarProfileLang(
        "Inloggad som",
        "Du kör",
        "Välj språk",
        "Logga in med",
        "Logga ut",
        "Laddning av profil misslyckades.",
        "Inloggning misslyckades.",
        "Karta",
        "Version",
        "Ingenting här."
      ),
      CarSettingsLang("Inställningar", "Öppna inställningar", "Välj bil", "Inga bilar."),
      PermissionsLang(
        "Ge rättigheter",
        "Ge appen rättigheter till plats och bildata",
        "Appen behöver rättigheter för att kunna spara plats och bildata till ditt Car-Tracker konto.",
        "Pröva på nytt",
        "Öppna inställningar och ge appen rättigheter."
      ),
      CarStatsLang(
        "Hastighet",
        "Höjd",
        "Nattläge",
        "Dagläge",
        "Bearing",
        "Accuracy",
        "degrees",
        "meters",
        "Batterinivå",
        "Kapacitet",
        "Räckvidd",
        "Temperatur ute"
      )
    ),
    CarLang(
      "Car-Tracker",
      CarLanguage(Language.finnish, "Suomeksi"),
      CarProfileLang(
        "Käyttäjätunnus",
        "Ajat autoa",
        "Valitse kieli",
        "Kirjaudu palvelulla",
        "Kirjaudu ulos",
        "Käyttäjätietojen lataus epäonnistui.",
        "Kirjautuminen epäonnistui.",
        "Kartta",
        "Version",
        "Täällä ei ole mitään."
      ),
      CarSettingsLang("Asetukset", "Avaa asetukset", "Valitse auto", "Ei autoja."),
      PermissionsLang(
        "Anna oikeuksia",
        "Anna sovellukselle oikeudet lukea auton sijainti ja muut tiedot",
        "Sovellus tarvitsee oikeuksia tallentaakseen sijainti- ja muut tiedot Car-Tracker-tilillesi.",
        "Kokeile uudelleen",
        "Avaa asetukset ja anna sovellukselle oikeuksia."
      ),
      CarStatsLang(
        "Nopeus",
        "Korkeus",
        "Yötila",
        "Päivätila",
        "Suunta",
        "Tarkkuus",
        "astetta",
        "metriä",
        "Akun varaus",
        "Kapasiteetti",
        "Toimintamatka",
        "Ulkolämpötila"
      )
    )
  )

case class CarsConf(languages: NonEmptyList[CarLang]) derives Codec.AsObject
object CarsConf:
  val default = CarsConf(CarLanguages.default)
