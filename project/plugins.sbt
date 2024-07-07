scalaVersion := "2.12.19"

libraryDependencies ++= Seq(
  "com.malliina" %% "okclient" % "3.6.0",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.12.757"
)

val utilsVersion = "1.6.39-SNAPSHOT"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.github.sbt" % "sbt-native-packager" % "1.10.0",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.2",
  "com.eed3si9n" % "sbt-assembly" % "2.2.0",
  "com.github.sbt" % "sbt-native-packager" % "1.10.0",
  "org.scalameta" % "sbt-mdoc" % "2.5.3"
) map addSbtPlugin
