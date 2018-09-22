name := "Personal1"

version := "1.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.16"
)


libraryDependencies ++= Seq(
"com.typesafe.akka" %% "akka-http"   % "10.1.5",
"com.typesafe.akka" %% "akka-stream" % "2.5.16",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5"
)

// Spray (Akka HTTP/JSON extension)
libraryDependencies ++= Seq(
"io.spray" %% "spray-can" % "1.3.3",
 // "io.spray" %% "spray-routing-shapeless" % "1.3.3",
  "io.spray" %% "spray-json" % "1.3.2",
  "io.spray" %% "spray-testkit" % "1.3.3" % Test
)

scalaVersion := "2.11.11"

