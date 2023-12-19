import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Graph, OverflowStrategy}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.javadsl.SubFlow
import akka.stream.scaladsl.{Balance, FileIO, Flow, GraphDSL, Merge}
import akka.util.ByteString
import classes.caseclasses.{Dependency, MavenDependency}
import classes.{DepParser, MavenDependencyCollection}

import concurrent.duration.DurationInt
import java.nio.file.Paths
import scala.concurrent.ExecutionContextExecutor

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("SA1")
  implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher

  private val resourceFolder = "src/main/resources/"
  private val pathCSVFile = Paths.get(s"$resourceFolder/maven_dependencies.csv")

  val source = FileIO.fromPath(pathCSVFile)
  val csvParser = CsvParsing.lineScanner()
  val csvToMap = CsvToMap.toMapAsStrings()

  val flowDep: Flow[Map[String, String], MavenDependency, NotUsed] = Flow[Map[String, String]]
    .map(map => MavenDependency(
      DepParser.parseLibrary(map("groupId")),
      DepParser.parseDependency(map("artifactId")),
      DepParser.parseType(map("version"))))

  val flowGroup = Flow[MavenDependency]
    .groupBy(185, _.library)
    .fold(MavenDependencyCollection())((depCollection, dep) => depCollection.addDependency(dep))
    .mergeSubstreams
  
  val flowThrottle = Flow[MavenDependency].throttle(10, 1.second)
  val flowBuffer = Flow[MavenDependency].buffer(5, OverflowStrategy.backpressure)
  val flowShape = Flow.fromGraph(
    GraphDSL.create() {
      implicit builder =>
      import GraphDSL.Implicits._

      val balance = builder.add(Balance[MavenDependency](2))
      
      val merge = builder.add(Merge[MavenDependency](2))
  
    }
  )
}
