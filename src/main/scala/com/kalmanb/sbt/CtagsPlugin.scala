package com.kalmanb.sbt

import sbt.Keys._
import sbt.Load.BuildStructure
import sbt._
import sbt.complete.DefaultParsers._
import complete.Parser

object CtagsPlugin extends Plugin {

  /** Can be overridden to put in a different location */
  def ExternalSourcesDir = ".lib-src"

  // Cache for update report so that we only have to do it once per sbt session
  var ctagsSources: Map[ModuleID, File] = Map.empty

  val ctagsLoad = TaskKey[Unit]("ctagsLoad", "Downloads sources for dependencies so they can be added the project. This will download all dependencies sources")
  val ctagsAdd = InputKey[Unit]("ctagsAdd", "ctagsAdd <module-name> unzip the module src into .lib-src/ and re-run ctags")
  val ctagsRemove = InputKey[Unit]("ctagsRemove", "ctagsRemove <module> removes the module source and re-runs ctags")

  override def settings: Seq[Setting[_]] = Seq[Setting[_]](
    ctagsLoad <<= (thisProjectRef, state, defaultConfiguration) map {
      (thisProjectRef, state, conf) ⇒
        ctagsSources = getSources(thisProjectRef, state, conf.get)
    },
    ctagsAdd <<= InputTask(ctagsAddParser) { args ⇒
      (args, baseDirectory, streams) map { (args, base, streams) ⇒
        val toAdd = ctagsSources.filterKeys(_.name == args._2)
        toAdd foreach (source ⇒ {
          streams.log.info("Adding src for %s".format(args._2))
          unzipSource(sourceDir(base, source._1), source._1, source._2)
        })
        updateCtags(base)
      }
    },
    ctagsRemove <<= InputTask(ctagsRemoveParser) { args ⇒
      (args, baseDirectory, streams) map { (args, base, streams) ⇒
        val name = args._2
        val dirs = IO.listFiles(base / ExternalSourcesDir, DirectoryFilter)
        val toRemove = dirs.filter(_.getPath.endsWith(name))
        toRemove foreach (dir ⇒ {
          IO.delete(dir)
          streams.log.info("Removed src dir %s".format(dir.getPath.replaceAll(base + "/", "")))
        })
        updateCtags(base)
      }
    }
  )

  /**
   * Runs an sbt update including downloading sources.
   */
  def getSources(project: ProjectRef, state: State, conf: Configuration): Map[ModuleID, File] = {
    val report = evaluateTask(Keys.updateClassifiers in configuration, project, state)
    report match {
      case Some((_, Value(updateReport))) ⇒ {
        val configurationReport = (updateReport configuration conf.name).toSeq
        val artifacts = for {
          report ← configurationReport
          module ← report.modules
          (art, file) ← module.artifacts if (art.classifier == Some("sources")) // Only keep modules with src
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
    baseDirectory / ExternalSourcesDir / dir
  }

  /**
   * Runs after add and remove - can be overridden if a different command is required
   * or a different indexer is used
   */
  def updateCtags(baseDirectory: File): Unit = {
    "ctags" !
  }

  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Option[(sbt.State, sbt.Result[A])] = {
    EvaluateTask(Project.extract(state).structure, key, state, ref, EvaluateTask defaultConfig state)
  }

  import Project._
  val ctagsAddParser: Initialize[State ⇒ Parser[(Seq[Char], String)]] =
    resolvedScoped { ctx ⇒
      (state: State) ⇒
        val options = ctagsSources.map(_._1.name)
        val tokens = options.map(token(_))
        tokens.size match {
          case n if (n > 1)  ⇒ Space ~ tokens.reduce(_ | _)
          case n if (n == 1) ⇒ Space ~ tokens.head
          case _             ⇒ Space ~ token("you need to reload ctags (ctagsLoad)")
        }
    }

  val ctagsRemoveParser: Initialize[State ⇒ Parser[(Seq[Char], String)]] =
    (resolvedScoped, baseDirectory) { (ctx, base) ⇒
      (state: State) ⇒
        val sourcesDir = base / ExternalSourcesDir
        val dirs = IO.listFiles(sourcesDir, DirectoryFilter)
        val shortNames = dirs.map(_.getPath.replaceAll(sourcesDir.getPath + "/", ""))
        val tokens = shortNames.map(name ⇒ token(name))
        tokens.size match {
          case n if (n > 1)  ⇒ Space ~ tokens.reduce(_ | _)
          case n if (n == 1) ⇒ Space ~ tokens.head
          case _             ⇒ Space ~ token("no sources are currently included in %s".format(ExternalSourcesDir))
        }
    }
}

