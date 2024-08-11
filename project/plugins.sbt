scalaVersion := "2.12.19"

libraryDependencies ++= Seq(
  "com.malliina" %% "okclient" % "3.7.3",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.12.765"
)

val utilsVersion = "1.6.40"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.github.sbt" % "sbt-native-packager" % "1.10.4",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.2",
  "com.eed3si9n" % "sbt-assembly" % "2.2.0",
  "org.scalameta" % "sbt-mdoc" % "2.5.4"
) map addSbtPlugin
