scalaVersion := "2.12.17"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.12.380"

val utilsVersion = "1.4.0"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.malliina" %% "sbt-bundler" % utilsVersion,
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.github.sbt" % "sbt-native-packager" % "1.9.11",
  "ch.epfl.scala" % "sbt-scalajs-bundler" % "0.21.1",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0",
  "org.scala-js" % "sbt-scalajs" % "1.13.0",
  "io.spray" % "sbt-revolver" % "0.9.1",
  "org.scalameta" % "sbt-scalafmt" % "2.5.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.11.0",
  "com.eed3si9n" % "sbt-assembly" % "2.1.0"
) map addSbtPlugin
