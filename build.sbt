organization := "com.kalmanb.sbt"
            
name := "sbt-ctags"

version := "0.4.0-SNAPSHOT"

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.12", "0.13")

sbtPlugin := true

publishMavenStyle := false

publishArtifact in Test := false

publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))

libraryDependencies += "junit" % "junit" % "4.11" % "test"

libraryDependencies <+= scalaVersion { version ⇒
  if (version startsWith "2.10") "org.scalatest" %% "scalatest" % "2.0"
  else "org.scalatest" %% "scalatest" % "2.0.M6-SNAP3"
}

