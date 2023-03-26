scalaVersion := "2.12.17"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.12.411"

val utilsVersion = "1.6.12"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" % "sbt-filetree" % "0.4.1",
  "com.malliina" % "live-reload" % "0.5.0",
  "com.github.sbt" % "sbt-native-packager" % "1.9.11",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0",
  "org.scala-js" % "sbt-scalajs" % "1.13.0",
  "io.spray" % "sbt-revolver" % "0.9.1",
  "org.scalameta" % "sbt-scalafmt" % "2.5.0",
  "com.eed3si9n" % "sbt-buildinfo" % "0.11.0",
  "com.eed3si9n" % "sbt-assembly" % "2.1.1"
) map addSbtPlugin
