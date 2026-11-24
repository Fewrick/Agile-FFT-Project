scalaVersion := "2.13.14"

scalacOptions ++= Seq(
  "-feature",
  "-language:reflectiveCalls",
)

Test / parallelExecution := false

// Forward JVM system properties to forked processes
val forwardedProperties = Seq("samples", "width", "sampleRate", "recordingSamples", "clkFreq")

def forwardedJavaOptions: Seq[String] =
  forwardedProperties.flatMap { key =>
    sys.props.get(key).map(value => s"-D$key=$value")
  }

Compile / run / fork := true
Compile / run / javaOptions ++= forwardedJavaOptions

Test / fork := true
Test / javaOptions ++= forwardedJavaOptions

val chiselVersion = "3.6.1"
addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % chiselVersion
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.6.2"
libraryDependencies += "edu.berkeley.cs" % "ip-contributions" % "0.6.1"

libraryDependencies += "net.fornwall" % "jelf" % "0.9.0"