scalaVersion := "2.12.21"

libraryDependencies ++= Seq(
  "com.malliina" %% "okclient" % "6.14.2",
  "software.amazon.awssdk" % "s3" % "2.44.0"
)

val utilsVersion = "1.7.0"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.github.sbt" % "sbt-native-packager" % "1.11.7",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.5.5",
  "com.eed3si9n" % "sbt-assembly" % "2.3.1",
  "org.scalameta" % "sbt-mdoc" % "2.9.0"
) map addSbtPlugin
