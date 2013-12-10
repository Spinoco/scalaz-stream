package scalaz.stream

import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen, Properties}
import scala.concurrent.SyncVar
import scalaz._
import scalaz.stream.Process._
import scalaz.stream.async.mutable.{Topic, Queue}
import java.util.concurrent.{TimeUnit, Executors}
import scalaz.std.string._
import scalaz.syntax.equal._
import scalaz.std.anyVal._
import scalaz.std.list._
import scalaz.std.list.listSyntax._
import scalaz.std.string._

import org.scalacheck._
import Prop._
import Arbitrary.arbitrary
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scala.concurrent
import scalaz.\/-
import scalaz.-\/
import scalaz.stream.ReceiveY.{ReceiveR, ReceiveL}
import scalaz.\/._
import scalaz.-\/
import scalaz.stream.Process.Halt
import scalaz.stream.Step
import scalaz.\/-
import scalaz.stream.processes._

object LeakTest extends Properties("leak") {



  class Ex extends java.lang.Exception {
    override def fillInStackTrace(): Throwable = this
  }

/*
  property("find-a-leak") = secure {

    Thread.sleep(10000)
    println("starting")

    val count = 500000

    //two asyc queues that will be fed in from the other thread
    val (qA, qsA) = async.queue[String]
    val (qB, qsB) = async.queue[String]
    val sigTerm = async.signal[Boolean]

    val result = new SyncVar[Throwable \/ Unit]

    // sources of A and B merged together, then chunked and flatmapped to get only head and limit the resulting elements
    val mergedChunked = qsA merge qsB

    //Stream that interrupts on left side, that actually is only set intitially to false but never is set to true in this test
    //sigTerm.discrete.wye(mergedChunked)(wye.interrupt)

      mergedChunked.scan(0)({case (c,s) => c+1}).filter(_ % 100 == 0).map(println).run.runAsync({
        cb =>
          println("DONE WITH PROCESS")
          result.put(cb)
      })

    sigTerm.set(false).run

    println("Staring to feed")
    for {i <- 0 until count} yield {
      def put(q: Queue[String]) = {
        q.enqueue(i.toString)
         if (i % (count/100) == 0) println("fed "+ (i / (count / 100)) + "pct")
      }
      i match {
        case each3 if each3 % 3 == 0 => put(qB)
        case other                   => put(qA)
      }
    }

    println("Done with feeding")

    //refreshing the signal every single second
    val scheduler = Executors.newScheduledThreadPool(1)
    scheduler.scheduleAtFixedRate(new Runnable{
      def run() = {
        println("Refreshing")
        sigTerm.set(false).run
      }
    }, 0, 1, TimeUnit.SECONDS)


    //just awaiting on final result
    println(result.get(3000000))

    true
  }

*/

/*

  property("either") = secure {
    val w = wye.either[Int,Int]
    val s = Process(0 until 10:_*).toSource
    s.wye(s)(w).take(10).runLog.run.foreach(println)

    true
  }

*/



/*
 property("forwardFill") = secure {
    import concurrent.duration._
    val t2 = Process.awakeEvery(2 seconds).forwardFill.zip {
      Process.awakeEvery(100 milliseconds).take(100)
    }.map(v=>println(v)).run.run
    true
  }

 */

/*

  property("merge") = secure {
    import concurrent.duration._
    val sleepsL = Process.awakeEvery(1 seconds).take(3)
    val sleepsR = Process.awakeEvery(100 milliseconds).take(30)
    val sleeps = sleepsL merge sleepsR
    val p = sleeps.toTask
    val tasks = List.fill(10)(p.timed(500).attemptRun)
    tasks.forall(_.isRight)
  }
*/


/*
    property("feedL") = secure {
      println("------------------ LEFT")
      val w = wye.feedL(List.fill(10)(1))(process1.id)
      val x = Process.range(0,100).wye(halt)(w).runLog.run
      x.toList == (List.fill(10)(1) ++ List.range(0,100))
    }
 */
/*

    property("feedR") = secure {
      println("------------------ RIGHT")
      val w = wye.feedR(List.fill(10)(1))(wye.merge[Int])
      val x = Process.range(0,100).wye(halt)(w).runLog.run
      println(x.toList)
      x.toList == (List.fill(10)(1) ++ List.range(0,100))
    }

*/

/*

  property("either") = secure {
    val w = wye.either[Int,Int]
    val s = Process.constant(1).take(1)
    val r = s.wye(s)(w).runLog.run

    println(r)
    r.map(_.fold(identity, identity)).toList == List(1,1)
  }

*/

/*

  property("interrupt") = secure {
    val p1 = Process(1,2,3,4,6).toSource
    val i1 = repeatEval(Task.now(false))
    val v = (i1.wye(p1)(wye.interrupt)).runLog.run.toList
    v == List(1,2,3,4,6)
  }
*/

/*
  property("merge.million") = secure {
    val count = 1000000
    val m =
    (Process.range(0,count ) merge Process.range(0, count)).flatMap {
      (v: Int) =>
        if (v % 1000 == 0) {
          val e = new java.lang.Exception
          println(v,e.getStackTrace.length)
          if (e.getStackTrace.size > 1000) e.printStackTrace()

          emit(e.getStackTrace.length)
        } else {
         halt
        }
    }.fold(0)(_ max _)

    m.runLog.run.map(_ < 512) == Seq(true)

  }

*/
/*

  property("merge.deep") = secure {
    val count = 10000
    val deep = 100

    def src(of:Int) = Process.range(0,count).map((_,of))

    val merged =
    (1 until deep).foldLeft(src(0))({
      case (p,x) => p merge src(x)
    })

     val m =
      merged.flatMap {
        case (v,of) =>
          if (v % 500 == 0) {
            val e = new java.lang.Exception
            println(of,e.getStackTrace.length,v)
            emit(e.getStackTrace.length)
          } else {
            halt
          }
      }.fold(0)(_ max _)

   m.runLog.run.map(_ < 512) == Seq(true)

  }
*/

/*
  property("merge.million") = secure {
    val count = 1000000

    def source = Process.range(0,count)

    val m =
      (source merge source).flatMap {
        (v: Int) =>
          if (v % 1000 == 0) {
            val e = new java.lang.Exception
            println(v, e.getStackTrace.length)
            emit(e.getStackTrace.length)
          } else {
            halt
          }
      }.fold(0)(_ max _)

    m.runLog.run.map(_ < 512) == Seq(true)

  }
*/

/*

  // Subtyping of various Process types:
  // * Process1 is a Tee that only read from the left (Process1[I,O] <: Tee[I,Any,O])
  // * Tee is a Wye that never requests Both (Tee[I,I2,O] <: Wye[I,I2,O])
  // This 'test' is just ensuring that this typechecks
  object Subtyping {
    def asTee[I,O](p1: Process1[I,O]): Tee[I,Any,O] = p1
    def asWye[I,I2,O](t: Tee[I,I2,O]): Wye[I,I2,O] = t
  }


  implicit def EqualProcess[A:Equal]: Equal[Process0[A]] = new Equal[Process0[A]] {
    def equal(a: Process0[A], b: Process0[A]): Boolean =
      a.toList == b.toList
  }
  implicit def ArbProcess0[A:Arbitrary]: Arbitrary[Process0[A]] =
    Arbitrary(Arbitrary.arbitrary[List[A]].map(a => Process(a: _*)))

  property("basic") = forAll { (p: Process0[Int], p2: Process0[String], n: Int) =>
    val f = (x: Int) => List.range(1, x.min(100))
    val g = (x: Int) => x % 7 == 0
    val pf : PartialFunction[Int,Int] = { case x : Int if x % 2 == 0 => x}

    val sm = Monoid[String]

    println("*"*200, p, p2)
    println()

    ("yip" |: {
      val l = p.toList.zip(p2.toList)
      println(l)
      val r = p.toSource.yip(p2.toSource).runLog.timed(5000).run.toList
      println("RGHT", r)
      println("LFT", r)
      println("="*150,l == r, l, r  )
      (l === r)
    })
  }
*/
/*
  property("basic2") = secure {
  //  val p = Process(1)
  //  val p2 = Process("a")
  val p : Process[Nothing,Int] = emitSeq(List(1))
   val p2  : Process[Nothing,String] = halt
    println("$$$$$",p,p2)
    val l = p.toList.zip(p2.toList)
    println(l)
    val r = p.toSource.yip(p2.toSource).runLog.timed(1000).run.toList
    println(r)
    println("*"*150, l,  l == r)
    (l === r)
  }

*/

/*
  property("either.cleanup-out-halts") = secure {
    val syncL = new SyncVar[Int]
    val syncR = new SyncVar[Int]

    val l = Process.awakeEvery(10 millis) onComplete (eval(Task.delay(syncL.put(100))).drain)
    val r = Process.awakeEvery(10 millis) onComplete (eval(Task.delay(syncR.put(200))).drain)

    val e = (l either r).take(10).runLog.timed(3000).run

    (e.size == 10) :| "10 first was taken" &&
      (syncL.get(1000) == Some(100)) :| "Left side was cleaned" &&
      (syncR.get(1000) == Some(200)) :| "Right side was cleaned"

  }

  */




/*

  property("either.continue-when-left-done") = secure {
    val e = (Process.range(0, 20) either (awakeEvery(25 millis).take(20))).runLog.timed(5000).run
    println(e)
    (e.size == 40) :| "Both sides were emitted" &&
      (e.zipWithIndex.filter(_._1.isLeft).lastOption.exists(_._2 < 35)) :| "Left side terminated earlier" &&
      (e.zipWithIndex.filter(_._1.isRight).lastOption.exists(_._2 == 39)) :| "Right side was last"
  }

*/
/*

  property("either.continue-when-right-done") = secure {
    val e = ((awakeEvery(25 millis).take(20)) either Process.range(0, 20)).runLog.timed(5000).run
    (e.size == 40) :| "Both sides were emitted" &&
      (e.zipWithIndex.filter(_._1.isRight).lastOption.exists(_._2 < 35)) :| "Right side terminated earlier" &&
      (e.zipWithIndex.filter(_._1.isLeft).lastOption.exists(_._2 == 39)) :| "Left side was last"
  }

  */

/*
  property("either.cleanup-on-left") = secure {
    val e = (
      ((Process.range(0, 2) ++ eval(Task.fail(new Ex))) attempt (_ => Process.range(100, 102))) either
        Process.range(10, 20)
      ).runLog.timed(3000).run
    (e.collect { case -\/(\/-(v)) => v } == (0 until 2)) :| "Left side got collected before failure" &&
      (e.collect { case \/-(v) => v } == (10 until 20)) :| "Right side got collected after failure" &&
      (e.collect { case -\/(-\/(v)) => v } == (100 until 102)) :| "Left side cleanup was called"

  }

*/

/*
  property("either.cleanup-on-right") = secure {
    val e = (Process.range(10, 20) either
      ((Process.range(0, 2) ++ eval(Task.fail(new Ex))) attempt (_ => Process.range(100, 102)))
      ).runLog.timed(3000).run
    (e.collect { case \/-(\/-(v)) => v } == (0 until 2)) :| "Right side got collected before failure" &&
      (e.collect { case -\/(v) => v } == (10 until 20)) :| "Left side got collected after failure" &&
      (e.collect { case \/-(-\/(v)) => v } == (100 until 102)) :| "Right side cleanup was called"
  }
*/

/*
  property("either.fallback-on-right") = secure {
    val e = ((Process.range(10, 12)) either (Process.range(0, 2) ++ Process.range(100, 102))).runLog.timed(1000).run
    (e.collect { case \/-(v) => v } == (0 until 2) ++ (100 until 102)) :| "Right side collected with fallback" &&
      (e.collect { case -\/(v) => v } == (10 until 12)) :| "Left side collected"
  }
*/

/*
  property("either.cleanup-out-halts") = secure {
    val syncL = new SyncVar[Int]
    val syncR = new SyncVar[Int]

    val l = Process.awakeEvery(10 millis) onComplete (eval(Task.delay(syncL.put(100))).drain)
    val r = Process.awakeEvery(10 millis) onComplete (eval(Task.delay(syncR.put(200))).drain)

    val e = (l either r).take(10).runLog.timed(3000).run

    (e.size == 10) :| "10 first was taken" &&
      (syncL.get(1000) == Some(100)) :| "Left side was cleaned" &&
      (syncR.get(1000) == Some(200)) :| "Right side was cleaned"

  }
 */

/*
  property("either.terminate-on-both") = secure {
    val e = (Process.range(0, 20) either Process.range(0, 20)).runLog.timed(1000).run
    println(e)
    (e.collect { case -\/(v) => v } == (0 until 20).toSeq) :| "Left side is merged ok" &&
      (e.collect { case \/-(v) => v } == (0 until 20).toSeq) :| "Right side is merged ok"
  }

  */

/*
  property("runStep") = secure {
    val count = 100000

    //def source = Process((0 until count):_*).toSource

    def source = (Process.range(0,count))// ++ Process.suspend(eval(Task.fail(new Throwable("FOO"))))) onFailure  Process.suspend(eval(Task.delay(println("Foo1 + Foo2")))).drain

    def go(p:Process[Task,Int], acc:Seq[Int]) : Throwable \/ Seq[Int] = {
      p.runStep.run match {
        case Step(-\/(End),_,_) => \/-(acc)
        case Step(-\/(e),Halt(_),_) => -\/(e)
        case Step(-\/(e),t,_) =>
          println("Error hit", e)
          go(t,acc)
        case Step(\/-(a),t,_) => go(t,acc ++ a)
      }
    }

   val start = System.currentTimeMillis()
   go(source,Seq()) match {
     case -\/(e) => e.printStackTrace
     case \/-(a) =>
       val diff = System.currentTimeMillis() - start
       a.foreach(println)
       println("DIFF",diff)
   }



    true

  }

  */
/*
  property("runStep") = secure {
    def go(p:Process[Task,Int], acc:Seq[Throwable \/ Int]) : Throwable \/ Seq[Throwable \/ Int] = {
      p.runStep.run match {
        case Step(-\/(e),Halt(_),_) => \/-(acc)
        case Step(-\/(e),t,_) => go(t,acc :+ -\/(e))
        case Step(\/-(a),t,_) => go(t,acc ++ a.map(\/-(_)))
      }
    }

    val ex = new java.lang.Exception("pure")

    val p1 = Process.range(10,12)
    val p2 = Process.range(20,22) ++ Process.suspend(eval(Task.fail(ex))) onFailure(Process(100).toSource)
    val p3 = Process.await(Task.delay(1))(i=> throw ex,halt,emit(200)) //throws exception in `pure` code

    go((p1 ++ p2) onComplete p3, Vector()) match {
      case -\/(e) => false
      case \/-(c) =>
        c == List(
          right(10),right(11)
          , right(20),right(21),left(ex),right(100)
          , left(ex), right(200)
        )
    }

  }

  */
/*
  property("runStep.stackSafety") = secure {
    def go(p:Process[Task,Int], acc:Int) : Int = {
      p.runStep.run match {
        case Step(-\/(e),Halt(_),_) => acc
        case Step(-\/(e),t,_) => go(t,acc)
        case Step(\/-(a),t,_) => go(t,acc + a.sum)
      }
    }
    val s = 1 until 10000
    val p1 = s.foldLeft[Process[Task,Int]](halt)({case (p,n)=>Emit(Vector(n),p)})
    go(p1,0) == s.sum
  }
*/


/*
  property("journals.timed") = forAll {

    l: List[Int] =>
      (l.size > 0 && l.size < 10000) ==> {
        val (before,after) = l.splitAt(l.size/2)
        val first = feed(before)(Topic.journal.last(50 millis))

        Thread.sleep(100)
        val second =  feed(after)(first)

        (first.flush == before) :| "first before eviction were collected" &&
          (second.flush == after) :| "after time were evicted"
      }

  }
*/
/*

  property("runStep2") = secure {
    def go(p:Process[Task,Int], acc:Seq[Throwable \/ Int]) : Throwable \/ Seq[Throwable \/ Int] = {
      p.runStep.run match {
        case Step(-\/(e),Halt(_),_) => \/-(acc)
        case Step(-\/(e),t,_) => go(t,acc :+ -\/(e))
        case Step(\/-(a),t,_) => go(t,acc ++ a.map(\/-(_)))
      }
    }

    val ex = new java.lang.Exception("pure")

    val p1 = (Process.range(10,20) |> take(2)) onComplete(suspend(eval(Task.now(println(">>>>>1000")))).drain)
   // val p2 = Process.range(20,22) ++ Process.suspend(eval(Task.fail(ex))) onFailure(Process(100).toSource)
   // val p3 = Process.await(Task.delay(1))(i=> throw ex,halt,emit(200)) //throws exception in `pure` code

    go(p1, Vector()) match {
      case -\/(e) => false
      case \/-(c) =>
        c.foreach(println)
        c == List(
        )
    }

  }

  */
 /* property("merged.queue-drain") = secure {
    val(q1,s1) = async.queue[Int]
    val(q2,s2) = async.queue[Int]

    def close[A](s:String,q:Queue[A]) =  (suspend(eval(Task.delay{ println("closing" + s) ;q.close}))).drain

    val sync = new SyncVar[Throwable \/ IndexedSeq[Int]]
    (((s1 onComplete close("2",q2)) merge (s2 onComplete close("1",q1 ))).take(4)).runLog.runAsync(sync.put)

    (Process.range(1,10) to q1.toSink()).run.runAsync(_=>())

    sync.get(3000) == Some(\/-(Vector(1, 2, 3, 4)))

  }

*/

  /*
  property("merge.queue.left-cleanup") = secure {
    val(q1,s1) = async.queue[Int]
    val(q2,s2) = async.queue[Int]
    def close[A]( q:Queue[A]) =  (suspend(eval(Task.delay{  q.close}))).drain

    q1.enqueue(1)

    val s3 = Process(2).toSource onComplete(close(q2))


    val sync = new SyncVar[Throwable \/ IndexedSeq[Int]]
    ((s1 onComplete s2) merge s3).take(2).runLog.timed(3000).runAsync(sync.put)

    println(sync.get(3000))

    sync.get(3000) == Some(\/-(Vector(2,1)))
  }
  */

/*
  property("leak.take.first-and-last") = secure {

    def printUsed =  println(   Runtime.getRuntime.totalMemory()- Runtime.getRuntime.freeMemory())

    def runningFirstLast: Process1[String,(String,String)] = {
      def go(first:String, prev:String) :  Process1[String,(String,String)]  = {
        await1[String].flatMap {
          case s => emit(prev,s) ++ go(first,s)
        }
      }

      await1[String].flatMap {
        case s => go(s,s)
      }
    }

    Runtime.getRuntime.gc()

    println("BEFORE")
    printUsed


    println(
      (Process.range(0,100000).flatMap{v=> /*Thread.sleep(0);*/emit(v)}.map(i=>(i+"_")*1000000) |> runningFirstLast |> last).runLast.run
    )

    println("AFTER")
    printUsed

    Runtime.getRuntime.gc()

    println("AFTER GC")
    printUsed

   // Thread.sleep(10000)

  true

  }
*/

/*
    property("either.cleanup-out-halts") = secure {
    val syncL = new SyncVar[Int]
    val syncR = new SyncVar[Int]
    val syncO = new SyncVar[Int]

    val l = Process.awakeEvery(10 millis) onComplete   (eval(Task.fork(Task.delay{ Thread.sleep(500);syncL.put(100)})).drain)
    val r = Process.awakeEvery(10 millis) onComplete  (eval(Task.fork(Task.delay{ Thread.sleep(500);syncR.put(200)})).drain)

    val e = ((l either r).take(10) onComplete (eval(Task.delay(syncO.put(1000))).drain)).runLog.timed(3000).run

    (e.size == 10) :| "10 first was taken" &&
      (syncO.get(3000) == Some(1000)) :| "Out side was cleaned" &&
      (syncL.get(0) == Some(100)) :| "Left side was cleaned" &&
      (syncR.get(0) == Some(200)) :| "Right side was cleaned"

  }

  */

  property("feed1.stack-safety") = secure {


    val p1 = scan[Int,Int](0)({case (s,_) => s + 1 })
    val p2 = bufferAll[Int]
    def go(p:Process1[Int,Int], rem: Int) : Process1[Int,Int] = {
      if (rem == 0) p
      else go(feed(Seq(rem))(p), rem-1)
    }

    val res = go(p1, 100000)

    val rs = res.flush

    println(rs.size)

    true
  }

}


