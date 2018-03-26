import scalariform.formatter.preferences._

organization := "eu.shiftforward"
name := "sueca-judge-server"

scalaVersion := "2.12.4"

enablePlugins(SbtTwirl, JavaServerAppPackaging)

libraryDependencies ++= Seq(
  "com.h2database"                         % "h2"                      % "1.4.196",
  "com.softwaremill.akka-http-session"    %% "core"                    % "0.5.3",
  "com.typesafe"                           % "config"                  % "1.3.2",
  "com.typesafe.akka"                     %% "akka-actor"              % "2.5.9",
  "com.typesafe.akka"                     %% "akka-cluster"            % "2.5.9",
  "com.typesafe.akka"                     %% "akka-http"               % "10.0.11",
  "com.typesafe.akka"                     %% "akka-stream"             % "2.5.9",
  "com.typesafe.slick"                    %% "slick"                   % "3.2.1",
  "com.typesafe.slick"                    %% "slick-hikaricp"          % "3.2.1",
  "de.heikoseeberger"                     %% "akka-log4j"              % "1.6.0",
  "io.circe"                              %% "circe-core"              % "0.9.1",
  "io.circe"                              %% "circe-generic"           % "0.9.1",
  "io.circe"                              %% "circe-parser"            % "0.9.1",
  "joda-time"                              % "joda-time"               % "2.9.9",
  "net.ruippeixotog"                      %% "akka-testkit-specs2"     % "0.2.3",
  "org.apache.logging.log4j"               % "log4j-api"               % "2.10.0",
  "org.apache.logging.log4j"              %% "log4j-api-scala"         % "11.0",
  "org.jgrapht"                            % "jgrapht-core"            % "1.1.0",
  "org.postgresql"                         % "postgresql"              % "42.2.1",
  "org.apache.logging.log4j"               % "log4j-core"              % "2.10.0"      % "runtime",
  "org.apache.logging.log4j"               % "log4j-slf4j-impl"        % "2.10.0"      % "runtime",
  "org.specs2"                            %% "specs2-core"             % "4.0.2"       % "test"
)

scalariformPreferences := scalariformPreferences.value
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(DoubleIndentConstructorArguments, true)

maintainer in Docker := "ShiftForward <info@shiftforward.eu>"
dockerBaseImage := "shiftforward/openjdk-docker:8"
dockerRepository := Some("shiftforward")
