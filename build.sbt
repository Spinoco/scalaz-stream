organization := "spinoco"

name := "scalaz-stream"

version := (Option(System.getenv("BUILD_NUMBER")) orElse (Option(System.getProperty("BUILD_NUMBER")))).map(buildNo => {
                             "0.1.0." +  buildNo + "-SNAPSHOT"
                           }).getOrElse({
                             val df = new java.text.SimpleDateFormat("yyMMddHHmmss")
                             "0.1.0.T" + df.format(new java.util.Date()) + "-SNAPSHOT"
                           })
                         

scalaVersion := "2.10.1"

scalacOptions ++= Seq(
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps"
)

conflictManager := ConflictManager.strict

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-concurrent" % "7.0.4" ,
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.4" % "test" ,
  "org.scalacheck" %% "scalacheck" % "1.10.0" % "test"
)

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  MavenRepository("Spinoco releases", "https://maven.spinoco.com/nexus/content/repositories/releases/"),
  MavenRepository("Spinoco snapshots", "https://maven.spinoco.com/nexus/content/repositories/snapshots/")
)

publishTo <<= (version).apply { v =>
  val nexus = "https://maven.spinoco.com/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("Snapshots" at nexus + "nexus/content/repositories/snapshots")
  else
    Some("Releases" at nexus + "nexus/content/repositories/releases")
}

credentials += {
  Seq("build.publish.user", "build.publish.password").map(k => Option(System.getProperty(k))) match {
    case Seq(Some(user), Some(pass)) =>
      Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    case _ =>
      Credentials(Path.userHome / ".ivy2" / ".credentials")
  }
}

publishMavenStyle := true

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

net.virtualvoid.sbt.graph.Plugin.graphSettings

