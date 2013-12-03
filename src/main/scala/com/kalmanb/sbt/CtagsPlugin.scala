package com.kalmanb.sbt

import sbt.Keys._
import sbt.Load.BuildStructure
import sbt._
import sbt.complete.DefaultParsers._
import complete.Parser
import scala.collection.mutable

object CtagsPlugin extends Plugin {
  /** Can be overridden to put in a different location */
  def ExternalSourcesDir = ".lib-src"

  val ctagsDownload = TaskKey[Unit]("ctagsDownload",
    "Downloads sources for dependencies so they can be added the project. This will download all dependencies sources")
  val ctagsRemove = InputKey[Unit]("ctagsRemove",
    "ctagsRemove <module> removes the module source and re-runs ctags")

  // TODO - help text
  //def ctagsAdd = Command("ctagsAdd", "ctagsAdd <module-id> : unzip the module src into .lib-src/ and re-run ctags")(ctagsAddParser)
  def ctagsAdd = Command("ctagsAdd", Help("help", ("one", "two"), "detailed"))(ctagsAddParser) { (state, args) ⇒
    val baseDir = state.configuration.baseDirectory
    getAllModulesFromAllProjects(state).filter(_.name == args).toSeq match {
      case Nil ⇒
        println("Error could not find %s in dependencies".format(args)); None
      case (head :: tail) ⇒
        val splits = head.toString.split(":")
        val moduleID = new ModuleID(organization = splits(0), name = splits(1), revision = splits(2))
        val srcFile: Option[File] = getSrcFromIvy(state, moduleID)
        srcFile match {
          case None         ⇒ println("Error could not find source for %s, please try ctagsDownload".format(moduleID))
          case Some(srcJar) ⇒ unzipSource(sourceDir(baseDir, moduleID), moduleID, srcJar)
        }
        srcFile
    }
    updateCtags(baseDir)
    state
  }

  def getAllModulesFromAllProjects(state: State): Set[ModuleID] = {
    //val result = Project.evaluateTask(taskKey, state)
    val taskKey = Keys.allDependencies in Test
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
    val allModules: Set[ModuleID] = (projectRefs flatMap (getProjectModules)).toSet
    allModules
  }

  override def settings: Seq[Setting[_]] = Seq[Setting[_]](
    commands ++= Seq(ctagsAdd),
    ctagsDownload <<= (thisProjectRef, state, defaultConfiguration, streams) map {
      (thisProjectRef, state, conf, streams) ⇒
        streams.log.debug("Downloading artifacts for %s".format(thisProjectRef))
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
      case Some((_, Value(updateReport))) ⇒
        val configurationReport = (updateReport configuration conf.name).toSeq
        val artifacts = for {
          report ← configurationReport
          module ← report.modules
          (art, file) ← module.artifacts if (art.classifier == Some("sources")) // Only keep modules with src
        } yield module.module -> file
        artifacts.toMap
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

  def evaluateTask[A](key: TaskKey[A], ref: ProjectRef, state: State): Option[(sbt.State, sbt.Result[A])] = {
    EvaluateTask(Project.extract(state).structure, key, state, ref, EvaluateTask defaultConfig state)
  }

  import Project._
  lazy val ctagsAddParser: State ⇒ Parser[(Seq[Char], String)] =
    (state: State) ⇒ {
      val options = getAllModulesFromAllProjects(state) map (_.toString)
      val tokens = options map (token(_))
      tokens.size match {
        case n if (n > 1)  ⇒ Space ~ tokens.reduce(_ | _)
        case n if (n == 1) ⇒ Space ~ tokens.head
      }
    }

  def parser: Parser[Seq[(String, Any)]] = {
    null
  }

  lazy val ctagsRemoveParser: Initialize[State ⇒ Parser[(Seq[Char], String)]] =
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

