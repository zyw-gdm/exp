
version := "1.0"

scalaVersion  := "2.12.11"

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

lazy val root = Project(id = "root", base = file("."))
  .aggregate(
  HelloRemote
  )
lazy val HelloRemote = Project(id = "HelloRemote", base = file("HelloRemote"))
  .settings(
    libraryDependencies ++=
      akkaStack
  )

lazy val HelloLocal = Project(id = "HelloLocal", base = file("HelloLocal"))
  .settings(
    libraryDependencies ++=
      akkaStack
  )
  .dependsOn(HelloRemote)



val akkaStack = Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.13",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.13",
  "com.typesafe.akka" %% "akka-remote" % "2.6.13"
)

