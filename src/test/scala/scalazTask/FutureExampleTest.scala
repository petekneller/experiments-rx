package scalazTask

import java.util.concurrent.TimeoutException

import org.scalatest.FunSuite

import scalaz.concurrent.Future
import scala.concurrent.duration._

class FutureExampleTest extends FunSuite {

  def printThread(prefix: String): Unit = println(s"$prefix executing on thread: ${Thread.currentThread().getName}")

  test("basic creation") {

    printThread("test")

    // 'now' lifts a value into the Future; NB it is strict!
    val f1 = Future.now { printThread("f1"); "f1" }
    f1.run
    f1.run

    // 'apply' is much like the classic scala Future - it schedules the body on the provided executor service.
    // However it won't actually schedule the body until one of the run methods is called
    val f2 = Future { printThread("f2"); "f2" }
    f2.run
    f2.run // note that calling this again, schedules the body again!

    val f4 = Future.schedule({ printThread("f4"); "f4" }, 500 millis)
    f4.run
    f4.run // note that this also re-schedules the body

    // 'delay' lifts a _lazy_ value into the Future
    val f3 = Future.delay { printThread("f3"); "f3" }
    f3.run
    f3.run

    // 'fork' creates a Future that moves the computation to a different thread; it _doesn't_ schedule it immediately
    val f5 = Future.delay { printThread("f5"); "f5" }
    val f6 = Future.fork { printThread("forking f5"); f5 }
    f6.run

    //suspend, async?
  }

  test("Future does not make any attempt to deal with exceptions") {

    val f1 = Future.delay { throw new RuntimeException("boo!"); "f1" }
    val f2 = Future.fork(f1)
    // f2.run // this will block forever
    f2.runAsync{ _ => "and this will never get called!" }

    // 'runFor' will add a timeout to the execution before throwing a TimeoutException
    intercept[TimeoutException] {
      f2.runFor(500 millis)
    }

    // 'attemptRunFor' will make the async failure explicit
    println(f2.attemptRunFor(500 millis))
  }

  test("start") {

    println("start-----------------------------------")

    // create a long-running future
    val f1 = Future.delay { printThread("f1"); println("f1 pausing for a moment"); Thread.sleep(1500); println("f1 finishing"); "f1" }
    // do some other stuff that takes a while
    println("beginning long-running computation 1")
    Thread.sleep(1000)
    println("ending long-running computation 1")
    // block on the future - but of course it hasn't begun yet
    println(s"result 1 = ${f1.run}")


    // 'start' kicks off the Future and returns a Future that will block waiting for the result.
    // NB that if this Future is pure (executes on the calling thread; is not async) then this will block immediately
    val f2 = Future.delay { printThread("f2"); println("f2 pausing for a moment"); Thread.sleep(3000); println("f2 finishing"); "f2" }
    val f3 = f2.start
    println("beginning long-running computation 2")
    Thread.sleep(1000)
    println("ending long-running computation 2")
    println(s"result 2 = ${f3.run}")

    // 'forking' a Future will introduce the asynchrony necessary to not block this long running computation
    val f4 = Future.delay { printThread("f4"); println("f4 pausing for a moment"); Thread.sleep(3000); println("f4 finishing"); "f4" }
    val f5 = Future.fork(f4).start
    println("beginning long-running computation 3")
    Thread.sleep(1000)
    println("ending long-running computation 3")
    println(s"result 3 = ${f5.run}")

    println("end start-------------------------------")
  }

  test("forking") {

    val f1 = Future.delay { printThread("f1"); "f1" }
    val f2 = f1 map { v => printThread("f2"); v }
    val f3 = f2 flatMap { v => printThread("f3"); Future.delay(v) }
    //f3.run
    Future.fork(f3).run
  }

  ignore("how does the trampolining work?") {}

}
