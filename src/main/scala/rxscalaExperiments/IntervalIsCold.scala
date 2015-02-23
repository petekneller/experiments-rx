package rxscalaExperiments

import rx.lang.scala.Observable

import scala.concurrent.duration._

object IntervalIsCold extends App {

  // It seems that .interval is cold - a long wait before subscribing makes no difference
  val tick = Observable.interval(1000 millis)

  Thread.sleep(5000) // wait a couple of seconds so that I _should_ miss a few of the early notifications
  tick map { e => s"time: $e" } foreach { e => println(e) }

  // what about after its already warmed up?
  Thread.sleep(2500) // what happens if I subscribe mid-event?
  tick map { e => s"time 2: $e" } foreach { e => println(e) }

  // Nope, it starts again!

}
