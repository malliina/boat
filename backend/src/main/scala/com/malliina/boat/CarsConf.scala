package com.malliina.boat

import cats.data.NonEmptyList
import io.circe.Codec

case class AuthLang(ctaGoogle: String, instructions: String, additionalText: String)

case class CarProfileLang(
  signedInAs: String,
  driving: String,
  chooseLanguage: String,
  auth: AuthLang,
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

case class PermissionContent(title: String, message: String) derives Codec.AsObject

case class PermissionsLang(
  grantCta: String,
  grantAccess: String,
  explanation: String,
  tryAgain: String,
  openSettingsText: String,
  car: PermissionContent,
  location: PermissionContent,
  background: PermissionContent,
  all: PermissionContent
) derives Codec.AsObject

case class CarSettingsLang(title: String, openSettings: String, selectCar: String, noCars: String)
  derives Codec.AsObject

case class CarLanguage(code: Language, name: String) derives Codec.AsObject

case class NotificationLang(
  appRunning: String,
  enjoy: String,
  grantPermissions: String
) derives Codec.AsObject

case class CarLang(
  appName: String,
  language: CarLanguage,
  profile: CarProfileLang,
  settings: CarSettingsLang,
  permissions: PermissionsLang,
  stats: CarStatsLang,
  notifications: NotificationLang
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
        AuthLang(
          "Sign in with Google",
          "Sign in to store your rides in the cloud.",
          "Your information will not be shared with third parties."
        ),
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
        "Please open Settings and grant app-level permissions for this app.",
        PermissionContent(
          "Grant access to car data",
          "This app needs access to car data (speed, battery level, and so on) in order to save it to your Car-Tracker account."
        ),
        PermissionContent(
          "Grant access to location",
          "This app needs access to the car's location in order to save it to your Car-Tracker account."
        ),
        PermissionContent(
          "Grant access to background location",
          "This app needs background location access."
        ),
        PermissionContent(
          "Grant app access to location and car",
          "This app needs access to location and car properties in order to save them to your Car-Tracker account."
        )
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
      ),
      NotificationLang("Car-Tracker running", "Enjoy the drive!", "Please grant permissions.")
    ),
    CarLang(
      "Car-Tracker",
      CarLanguage(Language.swedish, "Svenska"),
      CarProfileLang(
        "Inloggad som",
        "Du kör",
        "Välj språk",
        AuthLang(
          "Logga in med Google",
          "Logga in för att spara dina åk i molnet.",
          "Din information delas inte med tredje parter."
        ),
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
        "Öppna inställningar och ge appen rättigheter.",
        PermissionContent(
          "Ge rättigheter till bildata",
          "Appen behöver tillgång till bildata (hastighet, batterinivå, osv.) för att kunna spara det till ditt Car-Tracker konto."
        ),
        PermissionContent(
          "Ge appen rättigheter till bilens plats",
          "Appen behöver tillgång till plats för att spara den till ditt Car-Tracker konto."
        ),
        PermissionContent(
          "Ge appen rättigheter till plats i bakgrunden",
          "Appen behöver tillgång till platsdata när den är i bakgrunden."
        ),
        PermissionContent(
          "Ge appen rättigheter till plats och bildata",
          "Appen behöver rättigheter för att kunna spara plats och bildata till ditt Car-Tracker konto."
        )
      ),
      CarStatsLang(
        "Hastighet",
        "Höjd",
        "Nattläge",
        "Dagläge",
        "Riktning",
        "Noggrannhet",
        "grader",
        "meter",
        "Batterinivå",
        "Kapacitet",
        "Räckvidd",
        "Temperatur ute"
      ),
      NotificationLang("Car-Tracker igång", "Njut av dagen!", "Appen behöver rättigheter.")
    ),
    CarLang(
      "Car-Tracker",
      CarLanguage(Language.finnish, "Suomeksi"),
      CarProfileLang(
        "Käyttäjätunnus",
        "Ajat autoa",
        "Valitse kieli",
        AuthLang(
          "Kirjaudu palvelulla Google",
          "Kirjautumisella tallennat ajosi pilveen.",
          "Tietojasi ei jaeta kolmansien osapuolten kanssa."
        ),
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
        "Avaa asetukset ja anna sovellukselle oikeuksia.",
        PermissionContent(
          "Anna sovellukselle oikeudet lukea auton tietoja",
          "Sovellus tarvitsee lukuoikeuksia tallentaakseen tiedot Car-Tracker-tilillesi."
        ),
        PermissionContent(
          "Anna sovellukselle oikeudet lukea auton sijaintitiedot",
          "Sovellus tarvitsee oikeuksia tallentaakseen sijaintitiedot Car-Tracker-tilillesi."
        ),
        PermissionContent(
          "Anna sovellukselle lukuoikeudet sijaintitietoihin taustalla",
          "Sovellus tarvitsee oikeudet sijaintitietoihin taustalla."
        ),
        PermissionContent(
          "Anna sovellukselle oikeudet lukea auton sijainti ja muut tiedot",
          "Sovellus tarvitsee oikeuksia tallentaakseen sijainti- ja muut tiedot Car-Tracker-tilillesi."
        )
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
      ),
      NotificationLang("Car-Tracker käynnissä", "Nauti ajosta!", "Anna sovellukselle oikeuksia.")
    )
  )

case class CarsConf(languages: NonEmptyList[CarLang]) derives Codec.AsObject

object CarsConf:
  val default = CarsConf(CarLanguages.default)
