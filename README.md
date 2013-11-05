# sbt-ctags - Work In Progress

Update ctags when sbt compiles. Assists when using vim for editing scala code.


## Usage
In your `project/plugins.sbt` add the following line:

    addSbtPlugin("org.kalmanb" % "sbt-ctags" % "0.1.0")

Add your jenkins url to your project settings:

    import org.kalmanb.sbt.CtagsPlugin._

    lazy val name = Project(
       ...
       settings = ctagsSettings ++ Seq( 
         tbc := "..."
      )
    )

## Configuration

You'll need to have ctags configured for scala an example `~/.ctags` is in `conf/ctags`

