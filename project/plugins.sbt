scalaVersion := "2.12.20"

libraryDependencies ++= Seq(
  "com.malliina" %% "okclient" % "3.7.5",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.12.780"
)

val utilsVersion = "1.6.43"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.github.sbt" % "sbt-native-packager" % "1.11.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.4",
  "com.eed3si9n" % "sbt-assembly" % "2.3.0",
  "org.scalameta" % "sbt-mdoc" % "2.6.2"
) map addSbtPlugin
