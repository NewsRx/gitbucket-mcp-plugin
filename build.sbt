name := "gitbucket-mcp-plugin"
organization := "com.newsrx"
version := "0.1.0"
scalaVersion := "2.13.18"

resolvers += "GitBucket Releases" at "https://oss.sonatype.org/content/repositories/releases/"

libraryDependencies ++= Seq(
  "io.github.gitbucket" %% "gitbucket" % "4.46.0" % "provided",
  "org.scalatra" %% "scalatra-javax" % "3.1.2" % "provided",
  "io.github.json4s" %% "json4s-jackson" % "4.1.0" % "provided",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
  "org.scalatest" %% "scalatest" % "3.2.18" % "test"
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false)

enablePlugins(AssemblyPlugin)