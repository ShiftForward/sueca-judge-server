package eu.shiftforward.suecajudge.safeexec

sealed trait Language {
  def name: String
  def makefileCompileResource: String
  def makefileRunResource: String
  def sourceFilename: String
  def compilesToFilename: String
}

case object C extends Language {
  def name = "C"
  def makefileCompileResource = "/safeexec/Makefile_compile_c"
  def makefileRunResource = "/safeexec/Makefile_run_c"
  def sourceFilename = "main.c"
  def compilesToFilename = "a.out"
}

case object Cpp extends Language {
  def name = "C++"
  def makefileCompileResource = "/safeexec/Makefile_compile_cpp"
  def makefileRunResource = "/safeexec/Makefile_run_cpp"
  def sourceFilename = "main.cpp"
  def compilesToFilename = "a.out"
}

case object Java extends Language {
  def name = "Java"
  def makefileCompileResource = "/safeexec/Makefile_compile_java"
  def makefileRunResource = "/safeexec/Makefile_run_java"
  def sourceFilename = "Main.java"
  def compilesToFilename = "Main.jar"
}

case object Python extends Language {
  def name = "Python"
  def makefileCompileResource = "/safeexec/Makefile_compile_py"
  def makefileRunResource = "/safeexec/Makefile_run_py"
  def sourceFilename = "main.py"
  def compilesToFilename = "main.cpython-36.pyc"
}

case object JavaScript extends Language {
  def name = "JavaScript"
  def makefileCompileResource = "/safeexec/Makefile_compile_js"
  def makefileRunResource = "/safeexec/Makefile_run_js"
  def sourceFilename = "main.js"
  def compilesToFilename = "main.js"
}

object Language {
  def apply(lang: String): Language = lang match {
    case "C" => C
    case "C++" => Cpp
    case "Java" => Java
    case "Python" => Python
    case "JavaScript" => JavaScript
    case other => throw UnsupportedLanguage(other)
  }

  case class UnsupportedLanguage(ext: String) extends Exception
}
