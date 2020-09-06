scalaVersion := "2.12.12"

scalacOptions ++= Seq("-unchecked", "-deprecation")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.856"

Seq(
  "com.typesafe.play" % "sbt-plugin" % "2.8.2",
  "com.malliina" % "sbt-utils-maven" % "1.0.0",
  "com.malliina" %% "sbt-nodejs" % "1.0.0",
  "com.malliina" %% "sbt-packager" % "2.9.0",
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.typesafe.sbt" % "sbt-gzip" % "1.0.2",
  "com.typesafe.sbt" % "sbt-digest" % "1.1.4",
  "ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.18.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0",
  "org.scala-js" % "sbt-scalajs" % "1.1.1",
  "com.vmunier" % "sbt-web-scalajs" % "1.0.11",
  "io.spray" % "sbt-revolver" % "0.9.1",
  "ch.epfl.scala" % "sbt-bloop" % "1.4.4",
  "org.scalameta" % "sbt-scalafmt" % "2.4.2",
  "com.eed3si9n" % "sbt-buildinfo" % "0.10.0"
) map addSbtPlugin
