package classes

import classes.caseclasses.Type._
import classes.caseclasses.{Dependency, Library, Type}

object DepParser {
  def parseLibrary(line: String): Library = {
    val fields = line.split(":")
    Library(fields(0), fields(1), fields(2))
  }

  def parseDependency(line: String): Dependency = {
    val fields = line.split(":")
    Dependency(fields(0), fields(1), fields(2))
  }

  def parseType(line: String): Type = line.toLowerCase() match {
      case "runtime" => Runtime
      case "compile" => Compile
      case _ => throw new Exception("Unknown type of dependency")
    }
}
