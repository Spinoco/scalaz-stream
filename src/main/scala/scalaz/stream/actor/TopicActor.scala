package scalaz.stream.actor

import scalaz.concurrent.{Actor, Strategy}
import scalaz.\/
import scalaz.\/._
import scala.collection.immutable.Queue
import scalaz.stream._
import scalaz.stream.Process._
import scalaz.stream.Process1


trait TopicActor {

  /**
   * Return actor and signal that forms the topic. Each subscriber is identified by `SubscriberRef` and have
   * its own Queue to keep messages that enqueue when the subscriber is processing the emitted
   * messages from publisher.
   *
   * There may be one or more publishers to single topic.
   *
   * Messages that are processed :
   *
   * message.topic.Publish      - publishes single message to topic
   * message.topic.Fail         - `fails` the topic. If the `End` si passed as cause, this topic is `finished`.
   *
   * message.topic.Subscribe    - Subscribes single subscriber and starts collecting messages for it
   * message.topic.UnSubscribe  - Un-subscribes subscriber
   * message.topic.Get          - Registers callback or gets messages in subscriber`s queue
   *
   *
   * Supplied journal is fed every published message to this actor. It is then consulted to fed the journaled messages
   * to subscribers for eventual reconciliation.
   *
   */
  def topic[A](journal:Process1[A,A])(implicit S:Strategy) :(Actor[message.topic.Msg[A]]) = {
    import message.topic._

    var subs = List[SubscriberRefInstance[A]]()

    //just helper for callback
    val open : Throwable \/ Unit = right(())

    //left when this topic actor terminates or finishes
    var terminated : Throwable \/ Unit = open

    var history : Process1[A,A] = journal

    @inline def ready = terminated.isRight


    val actor =  Actor.actor[Msg[A]] {

      //Publishes message in the topic
      case Publish(a,cb) if ready =>
        history = history.feed1(a)
        subs.foreach(_.publish(a))
        S(cb(open))

      //Gets the value of the reference
      //it wil register call back if there are no messages to be published
      case Get(ref:SubscriberRefInstance[A@unchecked],cb) if ready =>
        ref.get(cb)

      //Stops or fails this topic
      case Fail(err, cb) if ready =>
        history = journal //journal cleanup
        subs.foreach(_.fail(err))
        subs = Nil
        terminated = left(err)
        S(cb(terminated))


      // Subscribes subscriber
      // When subscriber terminates it MUST send un-subscribe to release all it's resources
      case Subscribe(cb, reconcile, buffer) if ready =>
        val subRef = new SubscriberRefInstance[A](left(buffer),buffer)(S)
        val inJournal = history.flush
        subs = subs :+ subRef
        S {
          val reconciled = (emitAll(inJournal) |> reconcile).flush
          cb(right((reconciled,subRef)))
        }

      // UnSubscribes the subscriber.
      // This will actually un-subscribe event when this topic terminates
      // will also emit last collected data
      case UnSubscribe(subRef, cb) =>
        subs = subs.filterNot(_ == subRef)
        S(cb(terminated))


      ////////////////////
      // When the topic is terminated or failed
      // The `terminated.left` is safe from here

      case Publish(_,cb) => S(cb(terminated))

      case Get(ref:SubscriberRefInstance[A@unchecked],cb) => ref.flush(cb,terminated)

      case Fail(_,cb) =>  S(cb(terminated))

      case Subscribe(cb,_,_) => S(cb(terminated.bimap(t=>t,r=>sys.error("impossible"))))


    }
    actor
  }


}
