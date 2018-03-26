package eu.shiftforward.suecajudge.safeexec

import java.nio.file._
import java.nio.file.attribute.PosixFilePermission._

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source
import scala.sys.ShutdownHookThread
import scala.sys.process._
import scala.util.Try

import org.apache.logging.log4j.scala.Logging

import eu.shiftforward.suecajudge.safeexec.SafeExec._

class SafeExec {
  private[this] val dockerRunner: DockerRunner = new DockerRunner

  private[this] def compileStep(lang: Language, source: Array[Byte])(implicit ec: ExecutionContext): CompilationResult = synchronized {
    val submissionDir = Files.createTempDirectory(dockerRunner.mountPath, "compile")
    val sourceFilePath = submissionDir.resolve(lang.sourceFilename)
    Files.write(sourceFilePath, source)

    val makefileStream = getClass.getResourceAsStream(lang.makefileCompileResource)
    Files.copy(makefileStream, submissionDir.resolve("Makefile"))
    makefileStream.close()

    val cmpStdout = submissionDir.resolve("cmp-stdout")
    val cmpStderr = submissionDir.resolve("cmp-stderr")
    val cmpStats = submissionDir.resolve("cmp-stats")

    val exitCode = withFullPermissions(dockerRunner.mountPath, submissionDir, sourceFilePath) {
      dockerRunner.run(submissionDir)
    }

    val compilationExecution = Execution(cmpStdout, cmpStderr, cmpStats)
    val result = CompilationResult(
      exitCode,
      compilationExecution,
      Try(Files.readAllBytes(submissionDir.resolve(lang.compilesToFilename))).toOption)
    dockerRunner.cleanup()
    result
  }

  private[this] def runStep(lang: Language, compiledFile: Array[Byte], input: String)(implicit ec: ExecutionContext): ExecResult = synchronized {
    val submissionDir = Files.createTempDirectory(dockerRunner.mountPath, "run")
    val compiledFilePath = submissionDir.resolve(lang.compilesToFilename)
    Files.write(compiledFilePath, compiledFile)

    val makefileStream = getClass.getResourceAsStream(lang.makefileRunResource)
    Files.copy(makefileStream, submissionDir.resolve("Makefile"))
    makefileStream.close()

    val exeStdin = submissionDir.resolve("exe-stdin")
    val exeStdout = submissionDir.resolve("exe-stdout")
    val exeStderr = submissionDir.resolve("exe-stderr")
    val exeStats = submissionDir.resolve("exe-stats")

    val exitCode = withFullPermissions(dockerRunner.mountPath, submissionDir, compiledFilePath) {
      writeToFile(exeStdin, input)
      dockerRunner.run(submissionDir)
    }

    val runExecution = Execution(exeStdout, exeStderr, exeStats)
    val result = ExecResult(exitCode, runExecution)
    dockerRunner.cleanup()
    result
  }

  def compile(lang: Language, source: Array[Byte])(implicit ec: ExecutionContext): Future[CompilationResult] = {
    Future(compileStep(lang, source))
  }

  def run(lang: Language, compiledFile: Array[Byte], input: String)(implicit ec: ExecutionContext): Future[ExecResult] = {
    Future(runStep(lang, compiledFile, input))
  }

  def destroy(): Unit = dockerRunner.destroy()
}

object SafeExec {
  private final val safeExecImage = "shiftforward/safeexec:0.3"
  private final val safeexecPath: Path = {
    val dockerTmpMount = Option(System.getenv("SHARED_TMP_FOLDER")).getOrElse("/tmp")
    val path = Paths.get(dockerTmpMount).resolve("safeexec")
    if (!Files.exists(path)) Files.createDirectory(path)
    path
  }

  private class DockerRunner() extends Logging {
    val mountPath: Path = Files.createTempDirectory(safeexecPath, "docker-runner")
    val id = s"docker run --rm -td --net=none -v $mountPath:/tmp/submission $safeExecImage".lineStream.head
    logger.info(s"Docker container $id started")

    private[this] val shutdownHook: ShutdownHookThread = sys.addShutdownHook {
      kill()
      deepDelete(mountPath)
    }

    private[this] def deepDelete(path: Path, keepRoot: Boolean = false): Unit = try {
      if (Files.isDirectory(path)) {
        val ret = Seq("/usr/bin/env", "sh", "-c", s"rm -r ${path.toAbsolutePath.toString}/*").!(ProcessLogger(_ => ()))
        if (ret != 0) logger.error(s"Deep deletion of directory $path returned exit code $ret")
      }
      if (!Files.isDirectory(path) || !keepRoot)
        Files.delete(path)
    } catch {
      case ex: Exception => logger.error(s"Failed to deep delete directory $path: $ex")
    }

    private[this] def kill(): Unit = {
      logger.info(s"Killing Docker container $id")
      val ret = s"docker kill $id".!(ProcessLogger(_ => ()))
      if (ret != 0) logger.error(s"Killing Docker container $id failed with exit code $ret")
    }

    def run(runPath: Path): Int = s"docker exec $id ./run.sh ${runPath.getFileName}".!(ProcessLogger(_ => ()))
    def cleanup(): Unit = {
      deepDelete(mountPath, keepRoot = true)
    }
    def destroy(): Unit = {
      kill()
      deepDelete(mountPath)
      shutdownHook.remove()
      ()
    }
  }

  // We need to change the directory permissions so that the Docker user can freely read/write/execute stuff
  private def fileWithFullPermissions[T](path: Path)(f: => T): T = {
    val originalPermissions = Files.getPosixFilePermissions(path)
    Files.setPosixFilePermissions(
      path,
      Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE,
        GROUP_EXECUTE, GROUP_READ, GROUP_WRITE,
        OTHERS_EXECUTE, OTHERS_READ, OTHERS_WRITE).asJava)
    val result = f
    Files.setPosixFilePermissions(path, originalPermissions)
    result
  }

  private def withFullPermissions[T](paths: Path*)(f: => T): T = paths.toList match {
    case Nil => f
    case x :: Nil => fileWithFullPermissions(x)(f)
    case x :: xs => withFullPermissions(x)(withFullPermissions(xs: _*)(f))
  }

  private def writeToFile(file: Path, content: String): Unit =
    Files.write(file, content.getBytes("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

  private def readFromFile(file: Path): String = {
    val fileHandle = file.toFile
    if (fileHandle.exists()) {
      val source = Source.fromFile(fileHandle)
      val res = Try(source.mkString.trim).getOrElse("")
      source.close() // don't leave open file descriptors
      res
    } else ""
  }

  case class Execution(stdout: String, stderr: String, stats: Stats)
  object Execution {
    def apply(stdout: Path, stderr: Path, stats: Path): Execution =
      Execution(readFromFile(stdout), readFromFile(stderr), Stats(readFromFile(stats)))
  }

  sealed trait SafeExecResult {
    def dockerExitCode: Int
  }
  case class ExecResult(dockerExitCode: Int, runtime: Execution) extends SafeExecResult
  case class CompilationResult(dockerExitCode: Int, compilation: Execution, compiledFile: Option[Array[Byte]]) extends SafeExecResult
}
