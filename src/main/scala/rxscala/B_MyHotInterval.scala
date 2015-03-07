package rxscala

import scala.concurrent.duration._


object B_MyHotInterval extends App {

  val tick = HotInterval(1000 millis)

  Thread.sleep(2000) // wait a couple of seconds so that I miss a few of the early notifications
  tick map { e => s"time: $e" } foreach { e => println(e) }

  Thread.sleep(2500) // what happens if I subscribe mid-event?
  tick map { e => s"time 2: $e" } foreach { e => println(e) }

  // a non-sideffectful combinator (like map is supposed to be) does not seem to create a subscription
  tick map { e => val e2 = s"time 3: $e"; println(e2); e2 } // foreach { e => println(e) }

}
