organization := "com.kalmanb.sbt"
            
name := "sbt-ctags-testing"

version := "0.1.0-SNAPSHOT"

libraryDependencies ++= Seq() 

//libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"


lazy val subA = project in file("sub-a") settings (
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"
)

lazy val subB= project in file("sub-b") settings (
  //libraryDependencies += "org.slf4j" % "slf4j-parent" % "1.7.5"
  libraryDependencies += "com.icegreen" % "greenmail" % "1.3.1b"
)


//net.virtualvoid.sbt.graph.Plugin.graphSettings
