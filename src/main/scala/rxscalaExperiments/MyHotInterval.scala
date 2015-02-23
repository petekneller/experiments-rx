package rxscalaExperiments

import scala.concurrent.duration._


object MyHotInterval extends App {

  val tick = HotInterval(1000 millis)

  Thread.sleep(2000) // wait a couple of seconds so that I miss a few of the early notifications
  tick map { e => s"time: $e" } foreach { e => println(e) }

  Thread.sleep(2500) // what happens if I subscribe mid-event?
  tick map { e => s"time 2: $e" } foreach { e => println(e) }
}
