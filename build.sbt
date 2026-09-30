ThisBuild / scalaVersion     := "2.12.15"
ThisBuild / version          := "0.1.0"

val chiselVersion = "3.5.2"

lazy val commonSettings = Seq(
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
    ),
    scalacOptions ++= Seq(
      "-Xsource:2.11",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
)

lazy val rocketchip = (project in file("./rocket-chip"))

lazy val cpuss = (project in file("."))
  .settings(commonSettings)
  .dependsOn(rocketchip)
