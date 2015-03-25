package scalazTask

import scalaz.Id
import scalaz.stream.Process.Halt
import scalaz._
import scalaz.concurrent._
import scalaz.stream._
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
  implicit val listCatchable = Util.listCatchable
  println(s"b.runLog = ${b.runLog}")

  // Id monad
  val d: Process[Id, Int] = a
  implicit val idCatchable = Util.idCatchable
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

  val d = c.onHalt{ cause => Process.emit("boo!") }
  println(d)
  println(d.runLog.run)
}

object Combinators extends App {

  val b = Process(1) ++ Process(2) ++ Process(3)
  println(b.map(_+1))
  println(b.flatMap(m => Process.emit(m+1)))

  // Note how the below looks exactly like the above - the single process containing Seq(1, 2, 3) looks like it gets
  // unravelled during the flatMap to a Process of Seq(1) and a Process of the rest
  val a = Process(1, 2, 3)
  println(a.map(_+1))
  println(a.flatMap(m => Process.emit(m+1)))

  val c: Process[Task, Int] = Process.emit(1).repeat // look at the impl of .repeat - its wild!
  println(c)
  println(c.take(10).runLog.run)

  // this is an interesting one - shorthand for evaluating a single effect
  // - see my example above for how I read from a list, then compare to the impl of 'eval'
  val d = Process.eval(Task.delay{ 2 })
  println(d.runLog.run)
}

object ChangingMonadicContext extends App {

  implicit val idCatchable = Util.idCatchable
  implicit val listCatchable = Util.listCatchable
  
  val a: Process[Id, Int] = Process(1, 2, 3)
  println(a)
  println(a.runLog)
  val b = a.translate(new NaturalTransformation[Id, List] { def apply[A](a: Id[A]): List[A] = List(a) })
  println(b)
  println(b.runLog)
  
}

object Process1 extends App {
  // Process1 is a Process that uses an upstream process as its 'effect system' - mechanised with the 'Env' type
  val c: Process1[Int, Int] = Process.await(Process.Get[Int])(i => Process.emit(i * 2))
  println(c)
  val c2: Process[Task, Int] = Process(1, 2, 3) |> c
  println(c2)
  println(c2.runLog.run)

  // receive1 is the Process1 equivalent of await in Process0 - it awaits a single value from the upstream process
  // and then calls the supplied fn to decide how to proceed.
  // Contrast this example with the one above
  val b: Process[Task, Int] = Process(1, 2, 3) |> Process.receive1(i => Process.emit(i * 2))
  println(b)
  println(b.runLog.run)

  // await1 is the equivalent of my example above - it awaits a single item and then emits it
  // It's sort of the Process equivalent of List.head
  // NB that the following pipeline only awaits/emits 1 item from the upstream Process. And this is also a good example
  // of streams pull nature - the side effect is not run in this case because the second value is never requested
  val a: Process[Task, Int] = Process(1, 2) ++ Process{ println("boo!"); 2 } |> Process.await1
  println(a)
  println(a.runLog.run)

  // process1.lift is your basic operator to create Process1's out of fn's
  val d: Process[Task, Int] = Process(1, 2, 3) |> process1.lift(_ * 2)
  println(d)
  println(d.runLog.run)

  // feed
  val e: Process[Task, Int] = Process(1, 2, 3) |> process1.feed(Seq(4, 5, 6))(process1.id)
  println(e)
  println(e.runLog.run)
}

object Util {

  val idCatchable = new Catchable[Id] {
    override def attempt[A](a: Id[A]): Id[Throwable \/ A] = id.point(\/-(a))
    override def fail[A](err: Throwable): Id[A] = throw err
  }

  val listCatchable = new Catchable[List] {
    override def attempt[A](f: List[A]): List[Throwable \/ A] = f map { a => \/-(a) }
    override def fail[A](err: Throwable): List[A] = throw err
  }

}