package scalazTask

import scalaz.concurrent.Task
import scalaz.stream._

object ProcessToy1 extends App {

  val everBigger: Process[Task, Int] = Process.iterate(1)(_+1)

  val stringz = everBigger.flatMap(i => Process.fill(i)(s"bigger $i"))

  stringz.take(10).runLog.run.foreach(println(_))
}
