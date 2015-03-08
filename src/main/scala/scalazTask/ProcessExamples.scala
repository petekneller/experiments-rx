package scalazTask

import scalaz.{\/-, \/, Catchable}
import scalaz.concurrent._
import scalaz.stream.{Process0, Process}
import scalaz.std.list._

object ProcessNothing extends App {

//  val a: Process[Nothing, Int] = Process.emitAll(Seq(1, 2, 3))
  val a: Process[Nothing, Int] = Process.emitAll(Seq(1, 2, 3)) ++ Process.emitAll(Seq(4, 5, 6))
  println(s"a = $a")
  // println(a.run) // doesn't compile, no Catchable instance

  val c: Process[Task, Int] = a
  println(s"c.runLast = ${c.runLast}")
  println(s"c.runLast.run = ${c.runLast.run}")
  println(s"c.runLog.run = ${c.runLog.run}")

  // List isn't supported out of the box (no Catchable) but it doesn't seem too hard to do
  val b: Process[List, Int] = a
  implicit val listCatchable = new Catchable[List] {
    override def attempt[A](f: List[A]): List[Throwable \/ A] = f map { a => \/-(a) }
    override def fail[A](err: Throwable): List[A] = throw err
  }
  println(s"b.runLog = ${b.runLog}")

  // the Id monad?
}

object ProcessStructure extends App {

  // create some raw Emit, Await and run them manually

  // compose pure (emit) with non-pure (await) and see how it affects the shape/outcome of the stream

  // do some println's inside the stream to see how the computation progresses

  // how does |> work for Process1's?
}