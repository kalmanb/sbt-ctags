package com.kalmanb.sbt

import sbt.Keys._
import sbt.Load.BuildStructure
import sbt._
import sbt.complete.DefaultParsers._
import complete.Parser

object CtagsPlugin extends Plugin {

  val ExternalSourcesDir = ".lib-src"

  var ctagsSources: Map[ModuleID, File] = Map.empty

  val ctagsLoad = TaskKey[Unit]("ctagsLoad", "")
  val ctagsAdd = InputKey[Unit]("ctagsAdd", "")
  val ctagsUpdate = TaskKey[Unit]("ctagsRemove", "")

  import Project._
  val artifactIdParser: Initialize[State ⇒ Parser[(Seq[Char], String)]] =
    resolvedScoped { ctx ⇒
      (state: State) ⇒
        val options = ctagsSources.map(_._1.name)
        val tokens = options.map(token(_))
        tokens.size match {
          case n if (n > 1) ⇒ Space ~ tokens.reduce(_ | _)
          case n if (n > 1) ⇒ Space ~ tokens.head
          case _            ⇒ Space ~ token("you need to reload ctags (ctagsLoad)")
        }
    }

  val ctagsSettings = Seq[Setting[_]](
    ctagsLoad <<= (thisProjectRef, state, defaultConfiguration) map {
      (thisProjectRef, state, conf) ⇒
        ctagsSources = getSources(thisProjectRef, state, conf.get)
    },
    ctagsAdd <<= InputTask(artifactIdParser) { args ⇒
      (args, baseDirectory, streams) map { (args, base, streams) ⇒
        val toAdd = ctagsSources.filterKeys(_.name == args._2)
        toAdd foreach (source ⇒ {
          streams.log.info(s"Adding src for ${args._2}")
          unzipSource(sourceDir(base, source._1), source._1, source._2)
        })

        updateCtags
      }
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

