scalaVersion := "2.12.6"

resolvers ++= Seq(
  Resolver.bintrayRepo("malliina", "maven"),
  ivyRepo("malliina bintray sbt", "https://dl.bintray.com/malliina/sbt-plugins/")
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

Seq(
  "com.malliina" % "sbt-play" % "1.2.2",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.typesafe.sbt" % "sbt-less" % "1.1.2",
  "org.scala-js" % "sbt-scalajs" % "0.6.22",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.6"
) map addSbtPlugin

dependencyOverrides ++= Seq(
  "org.scala-js" % "sbt-scalajs" % "0.6.22",
  "org.webjars" % "webjars-locator-core" % "0.33",
  "org.codehaus.plexus" % "plexus-utils" % "3.0.17",
  "com.google.guava" % "guava" % "23.0"
)

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
