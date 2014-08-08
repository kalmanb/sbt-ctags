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
    Help("ctagsAdd", ("", ""), "ctagsAdd <module-id>(*) : unzip the module src into .lib-src/ and re-run ctags. Modules with [*NA] do not have sources - try ctagsDownload"))(ctagsAddParser) { (state, args) ⇒
      val searchTerm = args._1(0) match {
        case s if (s.endsWith("*")) ⇒ s.dropRight(1)
        case s                      ⇒ s
      }
      val baseDir = state.configuration.baseDirectory
      val allModules = getAllModulesFromAllProjects(state) map (_.toString)

      val updated = allModules.filter(_ startsWith searchTerm) flatMap { module ⇒
        val splits = module.split(":")
        val moduleID = new ModuleID(organization = splits(0), name = splits(1), revision = splits(2))
        val srcFile = getSrcFromIvy(state, moduleID)
        if (srcFile.exists) {
          val dest = sourceDir(baseDir, moduleID)
          unzipSource(srcFile, dest)
          println("Added source for %s".format(module))
          Some(moduleID)
        } else None
      }

      if (updated.size > 0)
        updateCtags(baseDir)
      else
        println("Error could not find source for %s, please try ctagsDownload".format(args._1(0)))

      state
    }

  def ctagsRemove = Command("ctagsRemove",
    Help("ctagsRemove", ("", ""), "ctagsRemove <module>(*) removes the module source and re-runs ctags"))(ctagsRemoveParser) { (state, args) ⇒
      {
        val searchTerm = args._1(0) match {
          case s if (s.endsWith("*")) ⇒ s.dropRight(1)
          case s                      ⇒ s
        }
        val baseDir = state.configuration.baseDirectory
        val toRemove = getAllLocalModuleSrcDirs(state).filter(fullPath ⇒ {
          val dir = fullPath.getPath.split("/").last
          dir.startsWith(searchTerm)
        })
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

  def ctagsShowCurrent = Command.command("ctagsShowCurrent",
    Help("ctagsShowCurrent", ("", ""), "ctagsShowCurrent show the current sources that have been included")) { state ⇒
      getAllLocalModuleSrcDirs(state) foreach { fullDir ⇒
        val name = fullDir.toString.split("/").last
        println(name)
      }
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
    commands ++= Seq(ctagsAdd, ctagsRemove, ctagsRemoveAll, ctagsShowCurrent),
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

  def getSrcFromIvy(state: State, moduleID: ModuleID): File = {
    getSrcFile(ivyDir(state), moduleID)
  }

  def ivyDir(state: State): File = {
    val extracted = Project.extract(state)
    extracted.get(ivyPaths).ivyHome match {
      case None ⇒
        throw new Exception("Error could not find ivyHome")
      case Some(dir) ⇒ dir / "cache"
    }
  }

  def getSrcFile(ivyDir: File, moduleID: ModuleID): File = {
    ivyDir / moduleID.organization / moduleID.name / "srcs" / (moduleID.name + "-" + moduleID.revision + "-sources.jar")
  }

  /**
   * Runs after add and remove - can be overridden if a different command is required
   * or a different indexer is used
   */
  def updateCtags(baseDirectory: File): Unit = {
    "ctags -R" !
  }

  import Project._
  def ctagsAddParser: (State) ⇒ Parser[(Seq[String], Seq[String])] = { (state) ⇒
    import sbt.complete.DefaultParsers._
    val modules = getAllModulesFromAllProjects(state)
    val available = modules map (module ⇒ {
      val name = module.toString.replace(":test", "").replace("()", "").trim
      if (getSrcFile(ivyDir(state), module).exists)
        name
      else
        name + "[*NA]"
    })
    val selectTests = distinctParser(available, true)
    val options = (token(Space) ~> token("--") ~> spaceDelimited("<option>")) ?? Nil
    selectTests ~ options
  }

  def ctagsRemoveParser: (State) ⇒ Parser[(Seq[String], Seq[String])] = { (state) ⇒
    import sbt.complete.DefaultParsers._
    val baseDir = state.configuration.baseDirectory
    val sourcesDir = baseDir / ExternalSourcesDir
    val dirs = IO.listFiles(sourcesDir, DirectoryFilter)
    val modules = dirs.map(_.getPath.replaceAll(sourcesDir.getPath + "/", "")).toSet
    val selectTests = distinctParser(modules, true)
    val options = (token(Space) ~> token("--") ~> spaceDelimited("<option>")) ?? Nil
    selectTests ~ options
  }

  def distinctParser(exs: Set[String], raw: Boolean): Parser[Seq[String]] = {
    import sbt.complete.DefaultParsers._
    val base = token(Space) ~> token(NotSpace - "--" examples exs)
    val recurse = base flatMap { ex ⇒
      val (matching, notMatching) = exs.partition(GlobFilter(ex).accept _)
      distinctParser(notMatching, raw) map { result ⇒ if (raw) ex +: result else matching.toSeq ++ result }
    }
    recurse ?? Nil
  }

  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Option[(sbt.State, sbt.Result[A])] = {
    EvaluateTask(Project.extract(state).structure, key, state, ref, EvaluateTask defaultConfig state)
  }

}

