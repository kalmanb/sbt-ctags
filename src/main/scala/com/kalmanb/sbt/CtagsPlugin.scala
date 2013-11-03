package com.kalmanb.sbt

import sbt.Keys._
import sbt.Load.BuildStructure
import sbt._

object CtagsPlugin extends Plugin {

  val ctagsAdd = TaskKey[Unit]("ctagsAdd", "")
  val ctagsRemove = TaskKey[Unit]("ctagsRemove", "")

  val ctagsSettings = Seq[Setting[_]](
    ctagsAdd <<= (thisProjectRef, buildStructure, state, allDependencies, defaultConfiguration) map {
      (thisProjectRef, structure, state, deps, conf) ⇒
        getSources(thisProjectRef, state, conf.get)
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
  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Option[(sbt.State, sbt.Result[A])] = {
    EvaluateTask(Project.extract(state).structure, key, state, ref, EvaluateTask defaultConfig state)
  }

  def updateCtags(): Unit = {
    println("TODO - update")
  }

}

