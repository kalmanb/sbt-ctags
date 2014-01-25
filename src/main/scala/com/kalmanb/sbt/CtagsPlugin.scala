package com.kalmanb.sbt

import sbt.Keys._
import sbt.Load.BuildStructure
import sbt._
import sbt.complete.DefaultParsers._
import complete.Parser
import scala.collection.mutable

object CtagsPluginDefault extends CtagsPlugin

class CtagsPlugin extends Plugin {
  /** Can be overridden to put in a different location */
  def ExternalSourcesDir = ".lib-src"

  def ctagsAdd = Command("ctagsAdd",
    Help("ctagsAdd", ("", ""), "ctagsAdd <module-id> : unzip the module src into .lib-src/ and re-run ctags"))(ctagsAddParser) { (state, args) ⇒
      val baseDir = state.configuration.baseDirectory
      val allModules = getAllModulesFromAllProjects(state) map (_.toString)

      allModules.filter(_ startsWith args._2).headOption match {
        case None ⇒
          println("Error could not find %s in dependencies".format(args)); None
        case Some(module) ⇒
          val splits = module.split(":")
          val moduleID = new ModuleID(organization = splits(0), name = splits(1), revision = splits(2))
          val srcFile: Option[File] = getSrcFromIvy(state, moduleID)
          srcFile match {
            case None ⇒ println("Error could not find source for %s, please try ctagsDownload".format(moduleID))
            case Some(srcJar) ⇒
              val dest = sourceDir(baseDir, moduleID)
              unzipSource(srcJar, dest)
          }
          updateCtags(baseDir)
      }
      state
    }

  def ctagsRemove = Command("ctagsRemove",
    Help("ctagsRemove", ("", ""), "ctagsRemove <module> removes the module source and re-runs ctags"))(ctagsRemoveParser) { (state, args) ⇒
      {
        val name = args._2
        val baseDir = state.configuration.baseDirectory
        val toRemove = getAllLocalModuleSrcDirs(state)
        deleteLocalDepSrcDir(toRemove)
        updateCtags(baseDir)
      }
      state
    }

  def ctagsRemoveAll = Command.command("ctagsRemoveAll",
    Help("ctagsRemoveAll", ("", ""), "ctagsRemoveAll removes the sources of all modules and re-runs ctags")) { state ⇒
      deleteLocalDepSrcDir(getAllLocalModuleSrcDirs(state))
      state
    }

  def getAllLocalModuleSrcDirs(state: State): Seq[File] = {
    val baseDir = state.configuration.baseDirectory
    IO.listFiles(baseDir / ExternalSourcesDir, DirectoryFilter)
  }

  def deleteLocalDepSrcDir(dirs: Seq[File]): Unit = {
    dirs foreach (dir ⇒ {
      IO.delete(dir)
      println("Removed src dir %s".format(dir.getPath.replaceAll(dir + "/", "")))
    })
  }

  val ctagsDownload = TaskKey[Unit]("ctagsDownload",
    "Downloads sources for dependencies so they can be added the project. This will download all dependencies sources. Can be done on a project by project basis")

  def ctagsSettings: Seq[Setting[_]] = Seq[Setting[_]](
    commands ++= Seq(ctagsAdd, ctagsRemove, ctagsRemoveAll),
    ctagsDownload <<= (thisProjectRef, state, defaultConfiguration, streams) map {
      (thisProjectRef, state, conf, streams) ⇒
        streams.log.debug("Downloading artifacts for %s".format(thisProjectRef))
        val structure = Project.extract(state).structure
        EvaluateTask(structure, Keys.updateClassifiers, state, thisProjectRef, EvaluateTask defaultConfig state)
    }
  )

  override def settings: Seq[Setting[_]] = {
    ctagsSettings
  }

  def getAllModulesFromAllProjects(state: State): Set[ModuleID] = {
    val structure = Project.extract(state).structure
    val projectRefs = structure.allProjectRefs

    def getProjectModules(ref: ProjectRef): Seq[ModuleID] = {
      val updateReport = EvaluateTask(structure, Keys.update, state, ref, EvaluateTask defaultConfig state)

      val modules: Seq[ModuleID] = for {
        (_, result) ← updateReport.toSeq
        report ← result.toEither.right.toOption.toSeq
        configReport ← report.configuration("test").toSeq
        allModules ← configReport.allModules
      } yield allModules
      modules
    }
    // Cache so we just load it once
    // Get dependencies for all projects
    val allModules: Set[ModuleID] = (projectRefs flatMap (getProjectModules)).toSet
    allModules
  }

  def unzipSource(sourceJar: File, dest: File): Unit = {
    IO.delete(dest)
    IO.createDirectory(dest)
    IO.unzip(sourceJar, dest)
  }

  def sourceDir(baseDirectory: File, moduleId: ModuleID): File = {
    val dir = moduleId.organization + "." + moduleId.name + ":" + moduleId.revision
    baseDirectory / ExternalSourcesDir / dir
  }

  def getSrcFromIvy(state: State, moduleID: ModuleID): Option[File] = {
    val extracted = Project.extract(state)
    val ivyDir = extracted.get(ivyPaths).ivyHome match {
      case None ⇒
        println("Error could not find ivyHome"); None
      case Some(dir) ⇒ Some(dir / "cache")
    }
    for { ivy ← ivyDir } yield getSrcFile(ivy, moduleID)
  }

  def getSrcFile(ivyDir: File, moduleID: ModuleID): File = {
    ivyDir / moduleID.organization / moduleID.name / "srcs" / (moduleID.name + "-" + moduleID.revision + "-sources.jar")
  }

  /**
   * Runs after add and remove - can be overridden if a different command is required
   * or a different indexer is used
   */
  def updateCtags(baseDirectory: File): Unit = {
    "ctags" !
  }

  import Project._
  lazy val ctagsAddParser: State ⇒ Parser[(Seq[Char], String)] =
    (state: State) ⇒ {
      val options = getAllModulesFromAllProjects(state) map (_.toString)
      val cleanOptions = options map (_.replace(":test", "").replace("()", "").trim)
      val tokens = (cleanOptions map (token(_))).toSet
      tokens.size match {
        case n if (n > 1)  ⇒ Space ~ tokens.reduce(_ | _)
        case n if (n == 1) ⇒ Space ~ tokens.head
      }
    }

  lazy val ctagsRemoveParser: State ⇒ Parser[(Seq[Char], String)] =
    (state: State) ⇒ {
      val baseDir = state.configuration.baseDirectory
      val sourcesDir = baseDir / ExternalSourcesDir
      val dirs = IO.listFiles(sourcesDir, DirectoryFilter)
      val shortNames = dirs.map(_.getPath.replaceAll(sourcesDir.getPath + "/", ""))
      val tokens = shortNames.map(name ⇒ token(name))
      tokens.size match {
        case n if (n > 1)  ⇒ Space ~ tokens.reduce(_ | _)
        case n if (n == 1) ⇒ Space ~ tokens.head
        case _             ⇒ Space ~ token("no sources are currently included in %s".format(ExternalSourcesDir))
      }
    }

  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Option[(sbt.State, sbt.Result[A])] = {
    EvaluateTask(Project.extract(state).structure, key, state, ref, EvaluateTask defaultConfig state)
  }

}

