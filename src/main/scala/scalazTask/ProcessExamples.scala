package scalazTask

import scalaz.stream.Process.Halt
import scalaz.{Id, \/-, \/, Catchable}
import scalaz.concurrent._
import scalaz.stream.{Cause, Process0, Process}
import scalaz.std.list._
import scalaz.Id._

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

  // Id monad
  val d: Process[Id, Int] = a
  implicit val idCatchable = new Catchable[Id] {
    override def attempt[A](a: Id[A]): Id[Throwable \/ A] = id.point(\/-(a))
    override def fail[A](err: Throwable): Id[A] = throw err
  }
  println(s"d.runLog = ${d.runLog}")
}

object ProcessStructure extends App {

  // note the difference in structure between the following, even though their output is identical
  println("emitAlls")
  println(Process.emitAll(Seq(1, 2, 3)))
  println(Process.emitAll(Seq(1, 2)) ++ Process.emitAll(Seq(3)))
  println(Process.emitAll(Seq(1)) ++ Process.emitAll(Seq(2)) ++ Process.emitAll(Seq(3)))

  println("emits, awaits and halts")
  // interesting that Process.halt doesn't halt a whole stream, just that process step
  val a: Process[Task, Int] = Process.emit(1) ++ Process.halt ++ Process.emit(2)
  println(a)
  println(a.runLog.run)
  println(Process.await(Task.delay{ 1 })(a => Process.emit(a)))
  println(Process.await(Task.delay{ 1 })(a => Process.emit(a)) ++ Process.halt)
  // ah, but halting has different causes
  val b: Process[Task, Int] = Process.emit(1) ++ Halt(Cause.Error(new RuntimeException)) ++ Process.emit(2)
  println(b)
  // println(b.runLog.run) // running this will cause an exception

  // create my own process that reads from a list one item at a time... sloppily
  val input = List(1, 2, 3)
  def go(i: List[Int]): Process[Task, Int] = i match {
    case head :: tail => Process.await(Task.delay{ head })(a => Process.emit(a) ++ go(tail))
    case Nil => Process.halt // Process.fail(new RuntimeException) // interesting that this exception doesn't get caught properly
  }
  val c = go(input)
  println(c)
  println(c.runLog.run)

  // do some println's inside the stream to see how the computation progresses

  // how does |> work for Process1's?
}