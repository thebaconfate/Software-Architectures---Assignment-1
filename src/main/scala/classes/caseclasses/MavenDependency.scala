package classes.caseclasses

import akka.util.ByteString
import classes.caseclasses.{Dependency, Library, Type}

case class MavenDependency(library: Library, dependancy: Dependency, Deptype: Type) {

}
