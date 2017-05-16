package fs2

import java.time.{LocalTime, Period}
import java.time.temporal.ChronoUnit.MICROS

object StateExamples1 extends App {

  def myTake(n: Int)(h: Handle[Task, Int]): Pull[Task,Int, Nothing] =
    for {
      (i, h) <- if(n <= 0) Pull.done else h.await1
      _ <- Pull.output1(i)
      r <- myTake(n-1)(h)
    } yield r


  println(Stream.emit(1).repeat.pull(myTake(7)).runLog.unsafeRun)

}

object StateExamples2 extends App {

  def counter(n: Int)(h: Handle[Task, LocalTime]): Pull[Task, String, Nothing] =
    for {
      (t, h) <- h.await1
      _ <- Pull.output1(s"$n: ${t.toString}")
      r <- counter(n+1)(h)
    } yield r

  val times = Stream.eval(Task.delay{ LocalTime.now() }).repeat

  println(times.pull(counter(1)).take(5).runLog.unsafeRun)

}

object StateExamples3 extends App {

 def timeDiff(last: LocalTime)(h: Handle[Task, LocalTime]): Pull[Task, Long, Nothing] =
   for {
     (t, h) <- h.await1
     _ <- Pull.output1(last.until(t, MICROS))
     r <- timeDiff(t)(h)
   } yield r

  val times = Stream.eval(Task.delay{ LocalTime.now() }).repeat

  println(times.pull(timeDiff(LocalTime.now())).take(5).runLog.unsafeRun)

}
