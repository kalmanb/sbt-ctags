organization := "com.kalmanb"
            
name := "sbt-ctags"

version := "0.1.0-SNAPSHOT"

sbtPlugin := true

publishMavenStyle := false

publishArtifact in Test := false

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.0.RC1-SNAP4",
  "junit" % "junit" % "4.11" % "test"
)

