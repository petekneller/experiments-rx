package scalazTask

import scalaz.concurrent.Task
import scalaz.stream._
import Process._
import scala.util.Random._

object Connections {

  case class Connection(i: Int)

  val init: Process[Task, Nothing] = await(Task.delay{ println("Connection pool init'd") })(_ => halt)
  val cleanup: Process[Task, Connection] = await(Task.delay{ println("Connection pool released") })(_ => halt)

  val connections: Process[Task, Connection] = init ++ unfold(1)(i => Some(i, i+1)).map(Connection(_)) onComplete{ cleanup }
  val fiveAttempts = connections.take(5)

  val console: Sink[Task, String] = constant((s: String) => Task.delay{ println(s) })

  class Data {
    var contents: List[String] = List.fill(20)(nextString(10))
    val initialSize = contents.size
    def take(): String = contents match {
      case h :: t => { contents = t; h }
      case Nil => { contents = Nil; throw new RuntimeException("data exhausted") }
    }

    def report: String = s"Data: initial [$initialSize]; remaining [${contents.size}]"
  }

  def readData(data: Data): Process[Task, String] = eval(Task.delay{ data.take() }).repeat
}

import Connections._

object Attempt0 extends App { // an infinite amount of data, bitten off in random-sized pieces

  def readSomeData(con: Connection): Process[Task, String] = {
    val numLinesBeforeFail = nextInt(5)
    fill(numLinesBeforeFail)(s"$con: ${nextString(10)}") ++ Process.fail(new RuntimeException("argh!"))
  }

  val results = fiveAttempts flatMap { con => readSomeData(con) }

  val log = (results observe console).runLog.run
  //  println(log)
}

object Attempt1 extends App { // same as above, but with fixed amount of data
  val data = new Data

  def readSomeData(con: Connection): Process[Task, String] = {
    val numLinesBeforeFail = nextInt(5)
    readData(data).map(s => s"$con: $s").take(numLinesBeforeFail) ++ Process.fail(new RuntimeException("argh!"))
  }

  val results = fiveAttempts flatMap { con => readSomeData(con) }

  val log = (results observe console).runLog.run
  //  println(log)
}

object Attempt2 extends App { // same as above with better failure handling on reading data,
  // allowing progress to the next connection
  val data = new Data

  def readSomeData(con: Connection): Process[Task, String] = {
    val numLinesBeforeFail = nextInt(5)
    readData(data).map(s => s"$con: $s").take(numLinesBeforeFail) ++ Process.fail(new RuntimeException("argh!")) onFailure(thr => await(Task.delay{ println(s"read failed with [$thr]") })(_ => halt))
  }

  val results = fiveAttempts flatMap { con => readSomeData(con) }

  val log = (results observe console).runLog.run
  println(data.report)
}

object Attempt3 extends App { // demo of what happens when you have enough conns to exhaust the whole data stream
  val data = new Data

  def readSomeData(con: Connection): Process[Task, String] = {
    val numLinesBeforeFail = nextInt(5)
    readData(data).map(s => s"$con: $s").take(numLinesBeforeFail) ++ Process.fail(new RuntimeException("argh!")) onFailure(thr => await(Task.delay{ println(s"read failed with [$thr]") })(_ => halt))
  }

  val results = connections.take(20) flatMap { con => readSomeData(con) }

  val log = (results observe console).runLog.run
  println(data.report)
}

object Attempt4 extends App { // explicitly catch the exhaustion of data and kill the rest of the process
  val data = new Data

  def readSomeData(con: Connection): Process[Task, String] = {
    val numLinesBeforeFail = nextInt(5)
    val dataChunk = readData(data).map(s => s"$con: $s") onFailure (_ => Process(()).kill)
    dataChunk.take(numLinesBeforeFail)  ++ Process.fail(new RuntimeException("argh!")) onFailure (thr => await(Task.delay{ println(s"read failed with [$thr]") })(_ => halt))
  }

  val results = connections.take(20) flatMap { con => readSomeData(con) }

  val log = (results observe console).runLog.run
  println(data.report)
}

// TODO:
//   use Writer to carry output instead of println?
