import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Graph, OverflowStrategy}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Balance, FileIO, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink}
import classes.caseclasses.{Dependency, Library, MavenDependency, Type}
import classes.{DepParser, DoubleCounter}
import concurrent.duration.DurationInt
import java.nio.file.Paths
import scala.collection.immutable
import scala.concurrent.{ExecutionContextExecutor, Future}

object Main extends App {
  implicit val actorSystem: ActorSystem = ActorSystem("SA1")
  implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher

  private val resourceFolder = "src/main/resources/"
  private val pathCSVFile = Paths.get(s"$resourceFolder/maven_dependencies.csv")

  private val source = FileIO.fromPath(pathCSVFile)
  private val csvParser = CsvParsing.lineScanner()
  private val csvToMap = CsvToMap.toMapAsStrings()

  private val flowDep: Flow[Map[String, String], MavenDependency, NotUsed] = Flow[Map[String, String]]
    .map(map => MavenDependency(
      DepParser.parseLibrary(map("library")),
      DepParser.parseDependency(map("dependency")),
      DepParser.parseType(map("type"))))

  private val flowGroup: Flow[MavenDependency, Map[Library, Seq[(Dependency, Type)]], NotUsed] = Flow[MavenDependency]
    .groupBy(185, _.library)
    .fold(Map.empty[Library, Seq[(Dependency, Type)]])((acc, dep) => {
      val lib = dep.library
      val depAndType = (dep.dependency, dep.depType)
      acc + (lib -> (acc.getOrElse(lib, Seq.empty) :+ depAndType))
    })
    .mergeSubstreams

  private val flowThrottle = Flow[Map[Library, Seq[(Dependency, Type)]]].throttle(10, 5.second)
  private val flowBuffer = Flow[Map[Library, Seq[(Dependency, Type)]]].buffer(5, OverflowStrategy.backpressure)

  private def countTypesFlow: Flow[Map[Library, Seq[(Dependency, Type)]], Map[Library, DoubleCounter], NotUsed] = Flow[Map[Library, Seq[(Dependency, Type)]]]
      .map(tempMap => {
        val newMap = tempMap.map((lib, seq) => {
          val counters = DoubleCounter()
          seq.foreach(depAndType => {
            val depType = depAndType._2
            depType match {
              case Type.Compile => counters.incComp()
              case Type.Runtime => counters.incRun()
            }
          })
          lib -> counters
        })
        newMap
      })

    private val sink: Sink[Map[Library, DoubleCounter], Future[Done]] = Sink.foreach(map => {
      map.foreach((lib, counter) => {
        println(s"Name: ${lib.groupId} ${lib.artifactId} ${lib.version} --> ${counter.toString()}")
      })
    })

    private val parallelCountingShape:
      Graph[FlowShape[Map[Library, Seq[(Dependency, Type)]], Map[Library, DoubleCounter]], NotUsed] =
      Flow.fromGraph(
      GraphDSL.create() {
        implicit builder =>
          import GraphDSL.Implicits._

          val balance = builder.add(Balance[Map[Library, Seq[(Dependency, Type)]]](2))
          val merge = builder.add(Merge[Map[Library, DoubleCounter]](2))

          balance.out(0) ~> countTypesFlow.async ~> merge.in(0)
          balance.out(1) ~> countTypesFlow.async ~> merge.in(1)
          FlowShape(balance.in, merge.out)
      })


  private val runnableGraph: RunnableGraph[Future[Done]] = source
    .via(csvParser)
    .via(csvToMap)
    .via(flowDep)
    .via(flowGroup)
    .via(flowThrottle)
    .via(flowBuffer)
    .via(parallelCountingShape)
    .toMat(sink)(Keep.right)


  runnableGraph.run().foreach(_ => actorSystem.terminate())

}

