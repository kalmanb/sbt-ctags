import sbt._
object PluginDef extends Build {
   override lazy val projects = Seq(root)
   lazy val root = Project("plugins", file(".")) dependsOn( ctags )
   lazy val ctags = uri("file:////home/kalmanb/work/sbt-ctags/")
}
