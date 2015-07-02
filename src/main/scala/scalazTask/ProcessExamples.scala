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

  // Note how the below looks exactly like the above - the single process coProcess(1): Process[Task, Int]ntaining Seq(1, 2, 3) looks like it gets
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

  // NB that although these two have the same results they are NOT the same
  // Process.constant lifts a pure value, whereas Process.eval.repeat implies re-evaluating side effects each time
  println(Process.eval(Task.delay{ 2 }).repeat.take(5).runLog.run)
  println((Process.constant(2).take(5): Process[Task, Int]).runLog.run)
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
  // It's kind of like a first-class Process version of
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

  // feed creates a Process1 that has baked into it the a seq of items that it will receive upon execution
  val e: Process[Task, Int] = Process(1, 2, 3) |> process1.feed(Seq(4, 5, 6))(process1.id)
  println(e)
  println(e.runLog.run)
}

object ChannelsAndSinks extends App {
  // Channel encapsulates the idea of a stream of effectful-functions
  
  // a trivial channel of constant behaviour that does some expensive op and then maps the given value
  val ch1: Channel[Task, Int, Int] = io.channel[Task, Int, Int](v => Task.delay{ /* expensive stuff happens here */ v * 2 })
  // or alternatively...
  val ch2: Channel[Task, Int, Int] = Process.constant(v => Task.delay{ /* expensive stuff happens here */ v * 2 })
  
  val a: Process[Task, Int] = Process(1, 2, 3)

  // values from a process can be passed through the channel like so
  val b = a zip ch1 flatMap { case (v, f) => Process.eval(f(v)) }
  println(b.runLog.run)
  // or, more succintly
  val c = a through ch1
  println(c.runLog.run)

  // a Sink is a Channel specialized to have Unit result
  val snk: Sink[Task, Int] = Process.constant(v => Task.delay{ println(v) })
  val d = a zip snk flatMap { case (v, f) => Process.eval(f(v)) }
  println(d.runLog.run)
  // or, more succintly
  val e = a to snk
  println(e.runLog.run)
  // however in this case its unlikely you care about the result values and sProcess(1): Process[Task, Int]o would use 'run' instead of 'runLog'
  println(e.run.run)

  // 'observe' is nice; just like 'tee' in bash it pipes the values to a sink as well as carrying them along as usual
  val f = a observe snk
  println(f.runLog.run)
}

object Tees extends App {
  // the most basic Tees zip things...
  val l: Process[Task, Int] = Process(1, 2, 3)
  val r: Process[Task, Int] = Process(4, 5, 6)
  println((l zip r).runLog.run)
  
  // or interleave things
  println((l interleave r).runLog.run)

  // the Tees themselves are actually attached using the .tee combinator of Process, specifying the other side and the Tee algorithmn
  val a = l.tee(r)(stream.tee.zip)
  println(a.runLog.run)
  val b = l.tee(r)(stream.tee.interleave)
  println(b.runLog.run)
  
  // the fundamental component of a Tee is the awaitL/R that fetches one side then stops; its the basis of the rest of the combinators
  val t1: Tee[Int, Int, Int] = Process.awaitL
  println(l.tee(r)(t1).runLog.run)
  val t2: Tee[Int, Int, Int] = for {
    l <- Process.awaitL[Int]
    r <- Process.awaitR[Int]
    out <- Process.emit(l) ++ Process.emit(r)
  } yield out
  println(l.tee(r)(t2).runLog.run)

  // some interesting Tees...

  // drainL/R and passL/R are similar, but one totally ignores a branch; the other reads, then ignores, a branch
  // the difference is apparent with side-effects
  val l2: Process[Task, Int] = Process(1) ++ Process{ println("boo!"); 2 } ++ Process(3)

  val c = l2.tee(r)(tee.drainL)
  println(c.runLog.run)
  val d = l2.tee(r)(tee.passR)
  println(d.runLog.run)

  // can use one branch of a tee to control when to read the other...
  val e: Process[Task, Boolean] = Process(false, true, false)
  val f = e.tee(r)(tee.when)
  println(f.runLog.run)

  val g = e.tee(r)(tee.until)
  println(g.runLog.run)
}

object FailureHaltKill extends App {

  // Halt is halt; every Process that doesnt' finish abnormally halts
  // the handler onHalt is the most general one of the handlers, of which there are examples below,
  // so I won't bother with explicit examples

  // Failure kills the Process, preventing it from returning anything; and exceptions are rethrown
  val p1: Process[Task, Int] = Process(1, 2, 3) ++ Process.fail(new RuntimeException("boo!")) ++ Process(4,5,6)
  try {
    println(p1.runLog.run)
  } catch {
    case e => println(e)
  }

  // all the handlers let you return a Process, so I guess you can continue any which way you want
  // the most obvious thing to would be to clean up resources
  // NB however that anything that occurs between the failure and the handler (ie. 4,5,6) is lost
  // (in an Append process)
  val p2 = p1 onFailure { thr => Process.await(Task.delay{ println("handling failure") })(_ => Process.halt) }
  println(p2.runLog.run)

  // but I guess you could always continue with some fallback path
  val p3 = p1 onFailure { thr => Process.await(Task.delay{ println("continuing with some other numbers") })(_ => Process(7,8,9)) }
  println(p3.runLog.run)

  // same occurs if the failure is downstream
  val p4 = p1 flatMap { i => if(i == 3) Process.fail(new RuntimeException("mwahaha!")) else Process(i) }
  try {
    println(p4.runLog.run)
  } catch {
    case thr => println(thr)
  }

  // actually cleanup would be better in the onComplete, that way it gets done rain or shine
  val p5 = p1 onComplete { Process.await(Task.delay{ println("cleaning up") })(_ => Process.halt) }
  try {
    println(p5.runLog.run) // thats interesting! you can't actually prevent the stream from failing
  } catch {
    case thr => println(thr)
  }

  // Kill is an interesting one; it's a signal to upstream to indicate the process should be stopped,
  // but existing output is still maintained
  val p6: Process[Task, Int] = Process(1,2,3) ++ Process(4).kill ++ Process(7,8,9)
  println(p6.runLog.run)
  val p7 = p6 flatMap { i => if(i == 3) Process(0).kill else Process(i) }
  println(p7.runLog.run)
}

object Writer extends App {

  // this is an interesting one!
  // Whereas the Writer monad allows you to carry output state around with a value, a Writer process
  // lets you do similar by interleaving output with values
  val p1: Process[Task, Int] = Process(1, 2, 3, 4, 5, 6)
  val p2 = Process.liftW(p1) flatMapO { i => Process.emitO(i) ++ (if (i % 2 == 0) Process.tell("fizz") else Process.empty) }
  val p3 = p2 flatMapO { i => Process.emitO(i.toString) ++ (if (i % 3 == 0) Process.tell("buzz") else Process.empty) }
  println(p3.runLog.run)
}

// TODO?
// wye gather, mergeN
// queues, signals, topics
// exchange

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