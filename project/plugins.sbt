scalaVersion := "2.12.21"

libraryDependencies ++= Seq(
  "com.malliina" %% "okclient" % "6.14.3",
  "software.amazon.awssdk" % "s3" % "2.45.1"
)

val utilsVersion = "1.7.1"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.github.sbt" % "sbt-native-packager" % "1.11.7",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2",
  "org.scalameta" % "sbt-scalafmt" % "2.6.1",
  "com.eed3si9n" % "sbt-assembly" % "2.3.1",
  "org.scalameta" % "sbt-mdoc" % "2.9.0",
  "org.typelevel" %% "sbt-fs2-grpc" % "3.1.2"
) map addSbtPlugin
