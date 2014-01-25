# sbt-ctags

sbt-ctags allows you to easily manage [Exuberant ctags](http://ctags.sourceforge.net/) for your project dependencies.

For use with [vim](http://www.vim.org/), [Emacs](http://www.gnu.org/software/emacs/), [Sublime Text 2](http://www.sublimetext.com/) and others.

## How it works - example
First update your dependencies:

    > ctagsDownload

Then select the dependency you wish to import for example `scala-library`:

    > ctagsAdd <tab>...
    > ctagsAdd org.scala-lang:scala-library...
    ... or ...
    > ctagsAdd org.scala-lang*
    ... or ...
    > ctagsAdd *

This will extract the `scala-library` source into `.lib-src/org.scala-lang.scala-library/` and run ctags over the whole project including the imported sources. Both Java and Scala libs can be added.

Now you can use ctags to navigate the `scala-library` source.

Make sure to add the following to your `.gitignore`

    .lib-src
    tags
    ctags

You can remove a library for your tags file with:

    > ctagsRemove <tab>...
    > ctagsRemove org.scala-lang...
    ... or ...
    > ctagsRemoveAll
    ... or ...
    > ctagsRemove *


## Installation

### sbt installation
In one of these locations:

    ~/.sbt/plugins/plugin.sbt          # for sbt 0.12
    ~/.sbt/0.13/plugins/plugins.sbt    # for sbt 0.13
    project/plugins.sbt                # only for your project

Add:

    addSbtPlugin("com.kalmanb.sbt" % "sbt-ctags" % "0.2.0")


### ctags installation
Install exuberant-ctags / ctags as per your OS.

Then you'll need to have ctags configured with scala definitions. An example is in [conf/ctags.example](https://github.com/kalmanb/sbt-ctags/blob/master/conf/ctags.example) copy/append it to `~/.ctags`

If you run the following you should get a `tags` file created.

    > cd <project>
    > ctags

### vim installation
Add the following to you `~/.vimrc`

    set tags=./tags,tags,../tags


### emacs installation
Please contribute


### sublime installation
See (https://github.com/SublimeText/CTags)

## Tasks

    > help ctagsDownload
    > help ctagsAdd 
    > help ctagsShowCurrent 
    > help ctagsRemove
    > help ctagsRemoveAll

## Configuration
The following can be customised as needed:

* The directory where sources are unzipped
* The command that gets called after ctagsAdd and ctagRemove. This allows you to call other external tools such as gtags etc.

For details on how to customise see: https://github.com/kalmanb/sbt-ctags/blob/master/conf/CustomCtagsPlugin.scala

## Contributing / Compiling
Go for it!

    > git clone https://github.com/kalmanb/sbt-ctags
    > cd sbt-ctags
    > sbt
    > ^^0.12         # Change sbt version
    > compile
    > ^ clean        # run command 'clean' for sbt versions

Testing, use the playground

    > cd sbt-ctags
    > sbt
    > ^^0.13
    > publish-local
    > exit
    > cd playground
    > sbt
    > ctagsDownload

Tip: you can run `~/publish-local` in the main dir and `reload` in the playground to update and test

## Change Log

### 0.3.0
Features:
* Add ctagsRemoveAll
* Add ctagsShowCurrent
* Add version numbers sources
* Support wildcards in ctagsAdd and ctagsRemove
* Prepend [*NA] for modules without sources

Bugfixes:
* File not found exception when source not available

### 0.2.0
Features:
* Allow customisation of destination directory to store sources
* Allow customisation of command executed when new sources are added and removed

### 0.1.0
Initial version:
* Add ctagsDownload
* Add ctagsAdd
* Add ctagsRemove

## Road Map
See (https://github.com/kalmanb/sbt-ctags/issues)

# License

Copyright (c) 2013 Kalman Bekesi

Published under the [Apache License 2.0](http://en.wikipedia.org/wiki/Apache_license).
