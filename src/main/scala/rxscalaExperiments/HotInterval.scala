package rxscalaExperiments

import java.util.{TimerTask, Timer}

import rx.lang.scala.{Subscription, Observer, Observable}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

object HotInterval {

  def apply(interval: Duration): Observable[Long] = {
    val observers = new ListBuffer[Observer[Long]]
    new Timer().scheduleAtFixedRate(new TimerTask {
      private var time = 0

      override def run(): Unit = {
        val os = observers.toList // snapshot the observer collection
        os foreach { o => o.onNext(time) }
        time = time + 1
      }
    }, 0, 1000)

    Observable.create[Long] { obs => observers += obs; Subscription() } // don't really care about subscription for this example
  }

}
