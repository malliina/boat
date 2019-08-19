scalaVersion := "2.12.8"

resolvers ++= Seq(
  Resolver.bintrayRepo("malliina", "maven"),
  ivyRepo("malliina bintray sbt", "https://dl.bintray.com/malliina/sbt-plugins/")
)

classpathTypes += "maven-plugin"

scalacOptions ++= Seq("-unchecked", "-deprecation")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.584"

Seq(
  "com.typesafe.play" % "sbt-plugin" % "2.7.3",
  "com.malliina" % "sbt-utils-maven" % "0.14.0",
  "com.malliina" %% "sbt-nodejs" % "0.14.0",
  "com.malliina" %% "sbt-packager" % "2.7.0",
  "com.malliina" % "sbt-filetree" % "0.3.0",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.typesafe.sbt" % "sbt-less" % "1.1.2",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.14.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0",
  "org.scala-js" % "sbt-scalajs" % "0.6.28",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.6",
  "com.typesafe.sbt" % "sbt-native-packager" % "1.3.7",
  "io.spray" % "sbt-revolver" % "0.9.1"
) map addSbtPlugin

def ivyRepo(name: String, urlString: String) =
  Resolver.url(name, url(urlString))(Resolver.ivyStylePatterns)
