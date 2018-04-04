# Sueca Judge Server [![Build Status](https://travis-ci.org/ShiftForward/sueca-judge-server.svg?branch=master)](https://travis-ci.org/ShiftForward/sueca-judge-server)

The judge system used by [ShiftForward](https://www.shiftforward.eu) to run the
[ShiftForward Challenge](https://enei2018.shiftforward.eu) at [ENEI](https://enei.pt) 2018.

## Quick Start

First of all, you'll need to create a file with a list of entry codes that can be used by participants to register.
You can do that by creating an `entry_codes.txt` file at the root of this project with one entry code per line.

Then, copy or rename [`src/main/resources/application.conf.template`](src/main/resources/application.conf.template) to
an `application.conf` file in the same location. You can tweak the settings there, which override the defaults present
in [`src/main/resources/reference.conf`](src/main/resources/reference.conf).

You'll need [SBT](https://www.scala-sbt.org) to build and run the server and [Docker](https://www.docker.com) to run
submissions in a safe environment. Once you have both installed in your system, you can run this server by executing:

```bash
sbt reStart
```

A web UI will be available at `http://localhost:8090` and the server will start running validations and tournaments
periodically.

## Launching as a Distributed System

This judge system is prepared to run as a distributed system in which the Web UI, a Runner Master (orchestrating the
validations and tournaments to run) and several Runner Workers (running the submissions as directed by the Runner
Master) are launched on different physical or virtual servers. Different command-line parameters can be passed to SBT to
change the behavior of the process:

```bash
# run the Web UI and an internal master-worker system
sbt reStart

# run the Web UI only
sbt reStart --http

# run a Runner Master only
sbt reStart --runner-master

# run a Runner Worker only
sbt reStart --runner-worker
```

The Web UI exchanges information with the Runner Master through the database only. The Runner Master forms a cluster
with all Runner Workers using [Akka Cluster](https://doc.akka.io/docs/akka/2.5/cluster-usage.html), allowing them to
exchange messages in real-time. Therefore, in order to get the system working in a distributed manner you need to
provide a persistent database and
[configure Akka Cluster](https://doc.akka.io/docs/akka/2.5/cluster-usage.html#a-simple-cluster-example) properly in your
`application.conf` (`reference.conf` already helps with most of that).
