package rxscala

import rx.lang.scala.Observable

import scala.concurrent.duration._

object D_HeatingUpAColdOne extends App {

  val tick = HotInterval(1000 millis)
  val tock = (Observable.just(999, 888, 777) ++ tick).publish

  tock map { e => s"time 1: $e" } foreach { println(_) }

  Thread.sleep(2000)
//  println("about to .connect")
//  tock.connect

  Thread.sleep(2000)

  tock map { e => s"time 2: $e" } foreach { println(_) }

  Thread.sleep(2000)
//  println("about to .connect")
//  tock.connect

  Thread.sleep(2500)

  tock map { e => s"time 3: $e" } foreach { println(_) }
  println("about to .connect")
  tock.connect

}
