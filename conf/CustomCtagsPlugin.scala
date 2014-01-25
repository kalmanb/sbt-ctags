import sbt._
import Keys._
import com.kalmanb.sbt.CtagsPlugin

/**
 * Example of how to override CtagsPlugin settings. Create a file
 * such as this in:
 *   ~/.sbt/plugins/CustomCtagsPlugin.scala       // sbt 0.12
 *   ~/.sbt/0.13/plugins/CustomCtagsPlugin.scala  // sbt 0.13
 *
 * or for a single project
 *  <project>/project/CustomCtagsPlugin.scala
 */
object CustomCtagsPlugin extends CtagsPlugin {

  /**
   * [Optional] - Allows you to define where sources are unzipped to.
   *
   * @return the directory to store library sources.
   *         this local to the project base directory
   */
  override def ExternalSourcesDir: String = "dir-name"

  /**
   * [Optional] - Allows you to override the function called after lib
   * sources are added. You can call shell commands such as ctags, gtags,
   * other indexers or tools. Called after ctagsAdd and ctagsRemove
   *
   * @param baseDirectory of the project if you need to access files within
   *        the project
   */
  override def updateCtags(baseDirectory: File): Unit = {
    /**
     * Default calls ctags
     * "ctags" !
     */
    println("show me !!")
  }

}
