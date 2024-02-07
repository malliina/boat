package com.malliina.boat.html

import scalatags.Text.all.*

object PrivacyPolicy extends BoatImplicits:
  val page =
    div(cls := "container privacy-container")(
      h1("Privacy Policy"),
      p(
        "This privacy policy describes how your information is used and stored when you use this app."
      ),
      p(
        "The purpose of using and storing your information is to enable app functionality and optimize your user experience. Your information is not used for any other purpose than enabling application features. Your information is not shared with third parties."
      ),
      p(
        "This app may communicate with networked servers to retrieve charts, icons, and tracks. The email address of signed in users is read for identification purposes. Other user data is not used. No personal data is shared with third parties."
      ),
      p("Network requests may be logged by server software.")
    )
