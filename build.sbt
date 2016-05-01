import com.typesafe.sbt.pgp.PgpKeys.publishSigned

val ReleaseTag = """^release/([\d\.]+a?)$""".r

lazy val contributors = Seq(
  "pchiusano" -> "Paul Chiusano",
  "pchlupacek" -> "Pavel Chlupáček",
  "alissapajer" -> "Alissa Pajer",
  "djspiewak" -> "Daniel Spiewak",
  "fthomas" -> "Frank Thomas",
  "runarorama" -> "Rúnar Ó. Bjarnason",
  "jedws" -> "Jed Wesley-Smith",
  "mpilquist" -> "Michael Pilquist"
)

lazy val commonSettings = Seq(
  organization := "co.fs2",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8", "2.12.0-M4"),
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    // "-Ywarn-dead-code", // Too buggy to be useful, for instance https://issues.scala-lang.org/browse/SI-9521
    "-Ywarn-value-discard",
    "-Ywarn-unused-import"
  ),
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) <<= (scalacOptions in (Compile, console)),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.0-M16-SNAP4" % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.1" % "test"
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/functional-streams-for-scala/fs2"), "git@github.com:functional-streams-for-scala/fs2.git")),
  homepage := Some(url("https://github.com/functional-streams-for-scala/fs2")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  initialCommands := s"""
    import fs2._
    import fs2.util._
  """,
  doctestWithDependencies := false,
  resolvers := Seq(
    MavenRepository("Spinoco releases", "https://maven.spinoco.com/nexus/content/repositories/spinoco_public_releases/")
    , MavenRepository("Spinoco snapshots", "https://maven.spinoco.com/nexus/content/repositories/spinoco_public_snapshots/")
  )

) ++ testSettings ++ scaladocSettings ++ publishingSettings ++ releaseSettings

lazy val testSettings = Seq(
  parallelExecution in Test := false,
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  publishArtifact in Test := true
)

lazy val scaladocSettings = Seq(
  scalacOptions in (Compile, doc) ++= Seq(
    "-doc-source-url", scmInfo.value.get.browseUrl + "/tree/master€{FILE_PATH}.scala",
    "-sourcepath", baseDirectory.in(LocalRootProject).value.getAbsolutePath,
    "-implicits",
    "-implicits-show-all"
  ),
  scalacOptions in (Compile, doc) ~= { _ filterNot { _ == "-Xfatal-warnings" } },
  autoAPIMappings := true
)

  lazy val publishingSettings = Seq(
    publishTo := {
      val nexus = "https://maven.spinoco.com/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("Snapshots" at nexus + "nexus/content/repositories/spinoco_public_snapshots")
      else
        Some("Releases" at nexus + "nexus/content/repositories/spinoco_public_releases")
    },
    credentials := {
    Seq("build.publish.user", "build.publish.password").map(k => Option(System.getProperty(k))) match {
      case Seq(Some(user), Some(pass)) =>
        Seq(Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass))
      case _ =>
        Seq(Credentials(Path.userHome / ".ivy2" / ".credentials"))
    }
  },
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>https://github.com/functional-streams-for-scala/fs2</url>
    <scm>
      <url>git@github.com:functional-streams-for-scala/fs2.git</url>
      <connection>scm:git:git@github.com:functional-streams-for-scala/fs2.git</connection>
    </scm>
    <developers>
      {for ((username, name) <- contributors) yield
      <developer>
        <id>{username}</id>
        <name>{name}</name>
        <url>http://github.com/{username}</url>
      </developer>
      }
    </developers>
  },
  pomPostProcess := { node =>
    import scala.xml._
    import scala.xml.transform._
    def stripIf(f: Node => Boolean) = new RewriteRule {
      override def transform(n: Node) =
        if (f(n)) NodeSeq.Empty else n
    }
    val stripTestScope = stripIf { n => n.label == "dependency" && (n \ "scope").text == "test" }
    new RuleTransformer(stripTestScope).transform(node)(0)
  }
)

lazy val releaseSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value
)

lazy val root = project.in(file(".")).
  settings(commonSettings).
  settings(
    publish := (),
    publishLocal := (),
    publishSigned := (),
    publishArtifact := false
  ).
  aggregate(core, io, benchmark)

lazy val core = project.in(file("core")).
  settings(commonSettings).
  settings(
    name := "fs2-core"
  )

lazy val io = project.in(file("io")).
  settings(commonSettings).
  settings(
    name := "fs2-io"
  ).dependsOn(core % "compile->compile;test->test")

lazy val benchmark = project.in(file("benchmark")).
  settings(commonSettings).
  settings(
    name := "fs2-benchmark",
    publish := (),
    publishLocal := (),
    publishArtifact := false
  ).dependsOn(io)

lazy val docs = project.in(file("docs")).
  settings(commonSettings ++ tutSettings).
  settings(
    name := "fs2-docs",
    tutSourceDirectory := file("docs") / "src",
    tutTargetDirectory := file("docs"),
    scalacOptions ~= {_.filterNot("-Ywarn-unused-import" == _)}
  ).dependsOn(core, io)

