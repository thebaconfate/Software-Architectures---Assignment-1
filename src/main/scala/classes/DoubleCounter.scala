package classes

import classes.caseclasses.{Dependency, Library, Type, MavenDependency}
import scala.collection.mutable

class DoubleCounter(var runtimeCount : Int = 0, var compileCount : Int= 0) {
  
  def incRun(): Unit = runtimeCount += 1
  def incComp(): Unit = compileCount += 1
  override def toString: String = s"Compile: $compileCount Runtime: $runtimeCount"
}
