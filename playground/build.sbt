organization := "com.kalmanb.sbt"
            
name := "sbt-ctags-testing"

version := "0.1.0-SNAPSHOT"

libraryDependencies ++= Seq() 

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"

lazy val sub = project in file("sub") settings (
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"
)
