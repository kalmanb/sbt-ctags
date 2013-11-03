organization := "com.kalmanb"
            
name := "sbt-ctags-testing"

version := "0.1.0-SNAPSHOT"

//scalaVersion in Global := "2.10.2"

libraryDependencies ++= Seq() 

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"

com.kalmanb.sbt.CtagsPlugin.ctagsSettings

//compile <<= (compile in Compile) dependsOn (ctags)
