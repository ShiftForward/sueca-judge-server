package eu.shiftforward.suecajudge.safeexec

import scala.concurrent.duration._
import scala.io.Source

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import eu.shiftforward.suecajudge.safeexec.SafeExec._
import eu.shiftforward.suecajudge.safeexec.Stats.Code
import eu.shiftforward.suecajudge.safeexec.Stats.Code.OK

class SafeExecSpec(implicit ee: ExecutionEnv) extends Specification with AfterAll {

  val safeExec = new SafeExec
  def afterAll(): Unit = safeExec.destroy()

  "a safe executor" should {

    "compile C code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.c").getFile).mkString.getBytes
      safeExec.compile(C, code) must beLike[CompilationResult] {
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), Some(comp)) if comp.length > 0 => ok
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), comp) =>
          ko("Empty compiled file result!")
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "run C code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.c").getFile).mkString.getBytes
      var compiled: Array[Byte] = null
      safeExec.compile(C, code) must beLike[CompilationResult] {
        case CompilationResult(0, _, Some(comp)) =>
          compiled = comp
          ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)

      safeExec.run(C, compiled, "input") must beLike[SafeExecResult] {
        case ExecResult(0, Execution("Hello World (C): input", _, Stats(Code.OK, _, _, _))) => ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "compile C++ code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.cpp").getFile).mkString.getBytes
      safeExec.compile(Cpp, code) must beLike[CompilationResult] {
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), Some(comp)) if comp.length > 0 => ok
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), comp) =>
          ko("Empty compiled file result!")
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "run C++ code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.cpp").getFile).mkString.getBytes
      var compiled: Array[Byte] = null

      safeExec.compile(Cpp, code) must beLike[CompilationResult] {
        case CompilationResult(0, _, Some(comp)) =>
          compiled = comp
          ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)

      safeExec.run(Cpp, compiled, "input") must beLike[SafeExecResult] {
        case ExecResult(0, Execution("Hello World (C++): input", _, Stats(Code.OK, _, _, _))) => ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "compile Java code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.java").getFile).mkString.getBytes
      safeExec.compile(Java, code) must beLike[CompilationResult] {
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), Some(comp)) if comp.length > 0 => ok
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), comp) =>
          ko("Empty compiled file result!")
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "run Java code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.java").getFile).mkString.getBytes
      var compiled: Array[Byte] = null

      safeExec.compile(Java, code) must beLike[CompilationResult] {
        case CompilationResult(0, _, Some(comp)) =>
          compiled = comp
          ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)

      safeExec.run(Java, compiled, "input") must beLike[SafeExecResult] {
        case ExecResult(0, Execution("Hello World (Java): input", _, Stats(Code.OK, _, _, _))) => ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "compile Python code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.py").getFile).mkString.getBytes
      safeExec.compile(Python, code) must beLike[CompilationResult] {
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), Some(comp)) if comp.length > 0 => ok
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), _) =>
          ko("Empty compiled file result!")
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "run Python code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.py").getFile).mkString.getBytes
      var compiled: Array[Byte] = null

      safeExec.compile(Python, code) must beLike[CompilationResult] {
        case CompilationResult(0, _, Some(comp)) =>
          compiled = comp
          ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)

      safeExec.run(Python, compiled, "input") must beLike[SafeExecResult] {
        case ExecResult(0, Execution("Hello World (Python): input", _, Stats(Code.OK, _, _, _))) => ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "compile JavaScript code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.js").getFile).mkString.getBytes
      safeExec.compile(JavaScript, code) must beLike[CompilationResult] {
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), Some(comp)) if new String(comp) == new String(code) => ok
        case CompilationResult(0, Execution(_, _, Stats(OK, _, _, _)), _) =>
          ko("Unexpected compiled file result!")
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "run JavaScript code" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/hello.js").getFile).mkString.getBytes
      var compiled: Array[Byte] = null

      safeExec.compile(JavaScript, code) must beLike[CompilationResult] {
        case CompilationResult(0, _, Some(comp)) =>
          compiled = comp
          ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)

      safeExec.run(JavaScript, compiled, "input") must beLike[SafeExecResult] {
        case ExecResult(0, Execution("Hello World (JavaScript): input", _, Stats(Code.OK, _, _, _))) => ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }

    "handle infinite loops" in {
      val code = Source.fromFile(getClass.getResource("/safeexec/infinite_loop.c").getFile).mkString.getBytes
      var compiled: Array[Byte] = null
      safeExec.compile(C, code) must beLike[CompilationResult] {
        case CompilationResult(0, _, Some(comp)) =>
          compiled = comp
          ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)

      safeExec.run(C, compiled, "input") must beLike[SafeExecResult] {
        case ExecResult(0, Execution("", "Starting infinite loop...", Stats(Code.TimeLimitExceeded, _, _, _))) => ok
        case res => ko(res.toString)
      }.await(0, 10.seconds)
    }
  }
}
