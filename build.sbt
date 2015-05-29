name := "ref-inliner"

scalaVersion := "2.11.6"

libraryDependencies ++=
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" ::
  "org.json4s" %% "json4s-native" % "3.2.10" ::
  "org.scala-lang.modules" %% "scala-async" % "0.9.2" ::
  "net.virtual-void" %%  "json-lenses" % "0.6.0" ::
  Nil
