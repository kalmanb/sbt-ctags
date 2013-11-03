package com.kalmanb.sbt

import sbt.Keys._
import sbt.Load.BuildStructure
import sbt._

object CtagsPlugin extends Plugin {

  val ExternalSourcesDir = ".lib-src"

  val ctagsAdd = TaskKey[Unit]("ctagsAdd", "")
  val ctagsRemove = TaskKey[Unit]("ctagsRemove", "")

  val ctagsSettings = Seq[Setting[_]](
    ctagsAdd <<= (thisProjectRef, buildStructure, state, allDependencies, defaultConfiguration, baseDirectory ) map {
      (thisProjectRef, structure, state, deps, conf, base) ⇒
        val sources = getSources(thisProjectRef, state, conf.get)
        //sources foreach { a =>
          //println (s"name: ${a._1.name}, file: $a._2")
        //}
        val toAdd = sources.filterKeys( _.name.contains("slf"))
        toAdd foreach (source => unzipSource(sourceDir(base, source._1), source._1, source._2))

        updateCtags
    }
  )

  def getSources(project: ProjectRef, state: State, conf: Configuration): Map[ModuleID, File] = {
    val report = evaluateTask(Keys.updateClassifiers in configuration, project, state)
    report match {
      case Some((_, Value(updateReport))) ⇒ { 
        val configurationReport = (updateReport configuration conf.name).toSeq
        val artifacts = for {
          report ← configurationReport
          module ← report.modules
          (art, file) ← module.artifacts if (art.classifier == Some("sources"))
        } yield module.module -> file
        artifacts.toMap
      }
      case _ ⇒ Map.empty
    }
  }

  def unzipSource(dest: File, moduleId: ModuleID, sourceJar: File): Unit = {
    IO.delete(dest)
    IO.createDirectory(dest)
    IO.unzip(sourceJar, dest)
  }

  def sourceDir(baseDirectory: File, moduleId: ModuleID): File = {
    val dir = moduleId.organization + "." + moduleId.name
    new File(baseDirectory, ExternalSourcesDir + "/" + dir)
  }

  def updateCtags(): Unit = {
    "ctags" !
  }

  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Option[(sbt.State, sbt.Result[A])] = {
    EvaluateTask(Project.extract(state).structure, key, state, ref, EvaluateTask defaultConfig state)
  }
}

