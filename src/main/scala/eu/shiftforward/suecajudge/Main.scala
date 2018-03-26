package eu.shiftforward.suecajudge

import eu.shiftforward.suecajudge.http.HttpServer
import eu.shiftforward.suecajudge.worker.{ RunnerMaster, RunnerWorker }

object Main extends App {
  if (args.isEmpty || args.contains("--http")) {
    new HttpServer().run()
  }

  if (args.isEmpty || args.contains("--runner")) {
    new RunnerMaster(true).run()
  } else {
    if (args.contains("--runner-master")) {
      new RunnerMaster(false).run()
    }
    if (args.contains("--runner-worker")) {
      new RunnerWorker().run()
    }
  }
}
