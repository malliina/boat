libraryDependencies ++= Seq(
  "com.malliina" %% "okclient" % "6.15.4",
  "software.amazon.awssdk" % "s3" % "2.45.1"
)

val utilsVersion = "2.0.3"

Seq(
  "com.malliina" %% "sbt-utils-maven" % utilsVersion,
  "com.malliina" %% "sbt-revolver-rollup" % utilsVersion,
  "com.malliina" %% "sbt-nodejs" % utilsVersion,
  "com.github.sbt" % "sbt-native-packager" % "1.11.7",
  "org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0",
  "org.scalameta" % "sbt-scalafmt" % "2.6.1",
  "com.eed3si9n" % "sbt-assembly" % "2.5.0",
  "org.scalameta" % "sbt-mdoc" % "2.9.0"
//  "org.typelevel" %% "sbt-fs2-grpc" % "3.1.2"
) map addSbtPlugin
