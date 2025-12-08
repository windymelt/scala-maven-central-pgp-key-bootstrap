val scala3Version = "3.7.4"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "scala-pgp-bootstrap",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "tech.neander"        %% "cue4s"         % "0.0.9",
      "com.softwaremill.ox" %% "core"          % "1.0.2",
      "com.lihaoyi"         %% "os-lib"        % "0.11.6",
      "com.outr"            %% "scribe"        % "3.17.0",
      "io.circe"            %% "circe-core"    % "0.14.15",
      "io.circe"            %% "circe-parser"  % "0.14.15",
      "io.circe"            %% "circe-generic" % "0.14.15",
    ),
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
  )

inThisBuild(
  List(
    organization := "dev.capslock",
    homepage     := Some(url("https://github.com/windymelt/scala-maven-central-pgp-key-bootstrap")),
    licenses     := List(
      "BSD-3-Clause" -> url("https://spdx.org/licenses/BSD-3-Clause.html"),
    ),
    developers := List(
      Developer(
        "windymelt",
        "Windymelt",
        "windymelt@capslock.dev",
        url("https://www.3qe.us"),
      ),
    ),
  ),
)
