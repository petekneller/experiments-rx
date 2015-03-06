package rxscalaExperiments

import scala.concurrent.duration._
import rx.lang.scala.Observable

object C_CoolingDownAHot extends App {

  val tick = HotInterval(1000 millis)
  // using Merge
//  val tock = Observable.just[Long](999, 888, 777).merge(tick)
  // using Concat
//  val tock = Observable.just[Long](999, 888, 777) ++ tick
  // using StartsWith
  val tock = 777 +: tick

  Thread.sleep(2500)
  tock map { e => s"time: $e" } foreach { println(_) }

  // wait a bit then add a subscriber
  Thread.sleep(2000)
  val tock2 = tock map { e => s"time 2: $e" }

  Thread.sleep(2500)
  tock2 foreach { e => println(e) }

  Thread.sleep(1500)
  tock2 map { e => s"time 3: $e" } foreach { println(_) }
}
