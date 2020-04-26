scalaVersion := "2.12.10"

scalacOptions ++= Seq("-unchecked", "-deprecation")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.584"

Seq(
  "com.typesafe.play" % "sbt-plugin" % "2.8.1",
  "com.malliina" % "sbt-utils-maven" % "0.16.1",
  "com.malliina" %% "sbt-nodejs" % "0.16.1",
  "com.malliina" %% "sbt-packager" % "2.8.4",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "com.typesafe.sbt" % "sbt-less" % "1.1.2",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler-sjs06" % "0.17.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "org.scala-js" % "sbt-scalajs" % "0.6.32",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.6",
  "com.typesafe.sbt" % "sbt-native-packager" % "1.7.0",
  "io.spray" % "sbt-revolver" % "0.9.1",
  "ch.epfl.scala" % "sbt-bloop" % "1.3.4",
  "org.scalameta" % "sbt-scalafmt" % "2.3.0"
) map addSbtPlugin
