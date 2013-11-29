package scalaz.stream.actor

import scalaz._
import scala.collection.immutable.Queue
import scalaz.concurrent.{Task, Strategy}
import scalaz.\/._
import scalaz.-\/
import scalaz.\/-
import scalaz.stream._

object message {

  private[actor] val okSignal = \/-(())

  object queue {
    trait Msg[A]
    case class Dequeue[A](callback: (Throwable \/ A) => Unit) extends Msg[A]
    case class Enqueue[A](a: A) extends Msg[A]
    case class Fail[A](error: Throwable, cancel: Boolean) extends Msg[A]
    case class Close[A](cancel: Boolean) extends Msg[A]

    def enqueue[A](a: A): Msg[A] =
      Enqueue(a)

    def dequeue[A](cb: A => Unit, onError: Throwable => Unit = t => ()): Msg[A] =
      Dequeue {
        case -\/(e) => onError(e)
        case \/-(a) => cb(a)
      }

    def close[A]: Msg[A] = Close[A](false)
    def cancel[A]: Msg[A] = Close[A](true)
    def fail[A](err: Throwable, cancel: Boolean = false): Msg[A] = Fail(err, cancel)
  }

  object ref {
    sealed trait Msg[A]
    case class Set[A](f:Option[A] => Option[A], cb:(Throwable \/ Option[A]) => Unit, returnOld:Boolean) extends Msg[A]
    case class Get[A](callback: (Throwable \/ (Int,A)) => Unit,onChange:Boolean,last:Int) extends Msg[A]
    case class Fail[A](t:Throwable, callback:Throwable => Unit) extends Msg[A]
  }




  object topic {
    sealed trait Msg[A]
    case class Publish[A](a:A, cb:(Throwable \/ Unit) => Unit) extends Msg[A]
    case class Fail[A](t:Throwable, cb:(Throwable \/ Unit) => Unit) extends Msg[A]

    case class Subscribe[A](cb:(Throwable \/ (Seq[A],SubscriberRef[A])) => Unit, reconcile:Process1[A,A], buffer:Process1[A,A]) extends Msg[A]
    case class UnSubscribe[A](ref:SubscriberRef[A], cb:(Throwable \/ Unit) => Unit) extends Msg[A]
    case class Get[A](ref:SubscriberRef[A], cb: (Throwable \/ Seq[A]) => Unit) extends Msg[A]


    //For safety we just hide the mutable functionality from the ref which we passing around
    sealed trait SubscriberRef[A]


    // all operations on this class are guaranteed to run on single thread,
    // however because actor`s handler closure is not capturing `cbOrQueue`
    // It must be tagged volatile
    final class SubscriberRefInstance[A](@volatile var cbOrQueue : Process1[A,A] \/  ((Throwable \/ Seq[A]) => Unit)
      , val buffer: Process1[A,A])(implicit S:Strategy)
      extends SubscriberRef[A] {

      //Publishes to subscriber or enqueue for next `Get`
      def publish(a:A)  =
        cbOrQueue = cbOrQueue.fold(
          pq => left(pq.feed1(a))
          , cb => {
            S(cb(right(List(a))))
            left(buffer)
          }
        )

      //fails the current call back, if any
      def fail(e:Throwable) = cbOrQueue.map(cb=>S(cb(left(e))))

      //Gets new data or registers call back
      def get(cb:(Throwable \/ Seq[A]) => Unit) =
        cbOrQueue = cbOrQueue.fold(
          l = { pq =>
            val q = pq.flush
            if (q.isEmpty) {
              right(cb)
            } else {
              S(cb(right(q)))
              left(buffer)
            }
          }
          , r = cb => {
            // this is invalid state cannot have more than one callback
            // we will fail this new callback
            S(cb(left(new Exception("Only one callback allowed"))))
            cbOrQueue
          }
        )

      //Fails the callback, or when something in Q, flushes it to callback
      def flush(cb:(Throwable \/ Seq[A]) => Unit, terminated: Throwable \/ Unit) =
        cbOrQueue = cbOrQueue.fold(
          l = pq => {
            val q = pq.flush
            if (q.isEmpty) cb(terminated.map(_=>Nil)) else cb(right(q))
            left(buffer)
          }
          , r = cb => {
            S(cb(left(new Exception("Only one callback allowed"))))
            cbOrQueue
          }

        )
    }

  }
}

