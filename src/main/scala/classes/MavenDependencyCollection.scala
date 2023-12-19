package classes

import classes.caseclasses.{Dependency, Library, Type, MavenDependency}

class MavenDependencyCollection() {
  
  var library: Option[Library] = None
  val dependencyCollection: scala.collection.mutable.Map[Dependency, Type] = scala.collection.mutable.Map()
  def addDependency(mavenDependency: MavenDependency): MavenDependencyCollection = {
    library match
      case None =>
        library = Some(mavenDependency.library)
        dependencyCollection += (mavenDependency.dependency -> mavenDependency.depType)
        this
      case Some(lib) =>
        if (lib == mavenDependency.library)
          dependencyCollection += (mavenDependency.dependency -> mavenDependency.depType)
          this
        else this  
  }
}
