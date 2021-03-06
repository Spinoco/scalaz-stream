organization := "spinoco"

name := "scalaz-stream"

version := (Option(System.getenv("BUILD_NUMBER")) orElse (Option(System.getProperty("BUILD_NUMBER")))).map(buildNo => {
                             "0.1.0." +  buildNo + "-SNAPSHOT"
                           }).getOrElse({
                             val df = new java.text.SimpleDateFormat("yyMMddHHmmss")
                             "0.1.0.T" + df.format(new java.util.Date()) + "-SNAPSHOT"
                           })
                         

scalaVersion := "2.10.2"

scalacOptions ++= Seq(
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps"
)

// https://github.com/sbt/sbt/issues/603
conflictWarning ~= { cw =>
  cw.copy(level = Level.Error, filter = (id: ModuleID) => true, group = (id: ModuleID) => id.organization + ":" + id.name)
}

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-concurrent" % "7.0.4-S1-SNAPSHOT",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.4-S1-SNAPSHOT" % "test",
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

pomIncludeRepository := Function.const(false)

pomExtra := (
  <url>http://typelevel.org/scalaz</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>http://www.opensource.org/licenses/mit-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/scalaz/scalaz-stream</url>
    <connection>scm:git:git://github.com/scalaz/scalaz-stream.git</connection>
    <developerConnection>scm:git:git@github.com:scalaz/scalaz-stream.git</developerConnection>
  </scm>
  <developers>
    <developer>
      <id>pchiusano</id>
      <name>Paul Chiusano</name>
      <url>https://github.com/pchiusano</url>
    </developer>
  </developers>
)

net.virtualvoid.sbt.graph.Plugin.graphSettings

