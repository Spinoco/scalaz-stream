package scalaz.stream

import collection.immutable.Queue
import concurrent.duration._

import scalaz.{\/, -\/, \/-}
import scalaz.\/._
import Process._
import scalaz.stream.ReceiveY._
import scalaz.stream.Process.Emit
import scalaz.stream.ReceiveY.ReceiveL
import scala.Some
import scalaz.stream.ReceiveY.ReceiveR
import scalaz.stream.Process.Halt
import scalaz.stream.Process.Env
import scalaz.stream.ReceiveY.HaltR
import scalaz.stream.Process.Await
import scalaz.stream.ReceiveY.HaltL

trait wye {

  import scalaz.stream.wye.{AwaitL, AwaitR, AwaitBoth}
  /**
   * A `Wye` which emits values from its right branch, but allows up to `n`
   * elements from the left branch to enqueue unanswered before blocking
   * on the right branch.
   */
  def boundedQueue[I](n: Int): Wye[Any,I,I] =
    yipWithL(n)((i,i2) => i2)

  /**
   * After each input, dynamically determine whether to read from the left, right, or both,
   * for the subsequent input, using the provided functions `f` and `g`. The returned
   * `Wye` begins by reading from the left side and is left-biased--if a read of both branches
   * returns a `These(x,y)`, it uses the signal generated by `f` for its next step.
   */
  def dynamic[I,I2](f: I => wye.Request, g: I2 => wye.Request): Wye[I,I2,ReceiveY[I,I2]] = {
    import wye.Request._
    def go(signal: wye.Request): Wye[I,I2,ReceiveY[I,I2]] = signal match {
      case L => awaitL[I].flatMap { i => emit(ReceiveL(i)) fby go(f(i)) }
      case R => awaitR[I2].flatMap { i2 => emit(ReceiveR(i2)) fby go(g(i2)) }
      case Both => awaitBoth[I,I2].flatMap {
        case t@ReceiveL(i) => emit(t) fby go(f(i))
        case t@ReceiveR(i2) => emit(t) fby go(g(i2))
        case _ => go(signal)
      }
    }
    go(L)
  }

  /**
   * A `Wye` which echoes the right branch while draining the left,
   * taking care to make sure that the left branch is never more
   * than `maxUnacknowledged` behind the right. For example:
   * `src.connect(snk)(observe(10))` will output the the same thing
   * as `src`, but will as a side effect direct output to `snk`,
   * blocking on `snk` if more than 10 elements have enqueued
   * without a response.
   */
  def drainL[I](maxUnacknowledged: Int): Wye[Any,I,I] =
    wye.flip(drainR(maxUnacknowledged))

  /**
   * A `Wye` which echoes the left branch while draining the right,
   * taking care to make sure that the right branch is never more
   * than `maxUnacknowledged` behind the left. For example:
   * `src.connect(snk)(observe(10))` will output the the same thing
   * as `src`, but will as a side effect direct output to `snk`,
   * blocking on `snk` if more than 10 elements have enqueued
   * without a response.
   */
  def drainR[I](maxUnacknowledged: Int): Wye[I,Any,I] =
    yipWithL[I,Any,I](maxUnacknowledged)((i,i2) => i)

  /**
   * Invokes `dynamic` with `I == I2`, and produces a single `I` output. Output is
   * left-biased: if a `These(i1,i2)` is emitted, this is translated to an
   * `emitSeq(List(i1,i2))`.
   */
  def dynamic1[I](f: I => wye.Request): Wye[I,I,I] =
    dynamic(f, f).flatMap {
      case ReceiveL(i) => emit(i)
      case ReceiveR(i) => emit(i)
      case HaltR(_) => halt
      case HaltL(_) => halt
    }

  /**
   * Nondeterminstic interleave of both inputs. Emits values whenever either
   * of the inputs is available.
   */
  def either[I,I2]: Wye[I,I2,I \/ I2] = {
    def go: Wye[I,I2,I \/ I2] =
      receiveBoth[I,I2,I \/ I2]({
        case ReceiveL(i) => emit(-\/(i)) fby go
        case ReceiveR(i) => emit(\/-(i)) fby go
        case other =>  go
       })
    go
  }

  /**
   * Continuous wye, that first reads from Left to get `A`,
   * Then when `A` is not available it reads from R echoing any `A` that was received from Left
   * Will halt once the
   */
  def echoLeft[A]: Wye[A, Any, A] = {
    def go(a: A): Wye[A, Any, A] =
      receiveBoth({
        case ReceiveL(l)  => emit(l) fby go(l)
        case ReceiveR(_)  => emit(a) fby go(a)
        case HaltOne(rsn) => Halt(rsn)
      })
    awaitL[A].flatMap(s => emit(s) fby go(s))
  }

  /**
   * Let through the right branch as long as the left branch is `false`,
   * listening asynchronously for the left branch to become `true`.
   * This halts as soon as the right branch halts.
   */
  def interrupt[I]: Wye[Boolean, I, I] = {
    def go[I]: Wye[Boolean, I, I] = awaitBoth[Boolean,I].flatMap {
      case ReceiveR(None) => halt
      case ReceiveR(i) => emit(i) ++ go
      case ReceiveL(kill) => if (kill) halt else go
      case HaltOne(e) => Halt(e)
    }
    go
  }

  /**
   * Non-deterministic interleave of both inputs. Emits values whenever either
   * of the inputs is available.
   *
   * Will terminate once both sides terminate.
   */
  def merge[I]: Wye[I,I,I] = {
    def go: Wye[I,I,I] =
      receiveBoth[I,I,I]({
        case ReceiveL(i) => emit(i) fby go
        case ReceiveR(i) => emit(i) fby go
        case other => go
      })
    go
  }

  /**
   * Like `merge`, but terminates whenever one side terminate.
   */
  def mergeHaltBoth[I]: Wye[I,I,I] = {
    def go: Wye[I,I,I] =
      receiveBoth[I,I,I]({
        case ReceiveL(i) => emit(i) fby go
        case ReceiveR(i) => emit(i) fby go
        case HaltOne(rsn) => Halt(rsn)
      })
    go
  }

  /**
   * Like `merge`, but terminates whenever left side terminates.
   * use `flip` to reverse this for the right side
   */
  def mergeHaltL[I]: Wye[I,I,I] = {
    def go: Wye[I,I,I] =
      receiveBoth[I,I,I]({
        case ReceiveL(i) => emit(i) fby go
        case ReceiveR(i) => emit(i) fby go
        case HaltL(rsn) => Halt(rsn)
        case HaltR(_) => go
      })
    go
  }

  /**
   * Like `merge`, but terminates whenever right side terminates
   */
  def mergeHaltR[I]: Wye[I,I,I] =
    wye.flip(mergeHaltL)

  /**
   * A `Wye` which blocks on the right side when either a) the age of the oldest unanswered
   * element from the left size exceeds the given duration, or b) the number of unanswered
   * elements from the left exceeds `maxSize`.
   */
  def timedQueue[I](d: Duration, maxSize: Int = Int.MaxValue): Wye[Duration,I,I] = {
    def go(q: Vector[Duration]): Wye[Duration,I,I] =
      awaitBoth[Duration,I].flatMap {
        case ReceiveL(d2) =>
          if (q.size >= maxSize || (d2 - q.headOption.getOrElse(d2) > d))
            awaitR[I].flatMap(i => emit(i) fby go(q.drop(1)))
          else
            go(q :+ d2)
        case ReceiveR(i) => emit(i) fby (go(q.drop(1)))
        case _ => go(q)
      }
    go(Vector())
  }

  /**
   * `Wye` which repeatedly awaits both branches, emitting any values
   * received from the right. Useful in conjunction with `connect`,
   * for instance `src.connect(snk)(unboundedQueue)`
   */
  def unboundedQueue[I]: Wye[Any,I,I] =
    awaitBoth[Any,I].flatMap {
      case ReceiveL(_) => halt
      case ReceiveR(i) => emit(i) fby unboundedQueue
      case _ => unboundedQueue
    }

  /** Nondeterministic version of `zip` which requests both sides in parallel. */
  def yip[I,I2]: Wye[I,I2,(I,I2)] = yipWith((_,_))

  /**
   * Left-biased, buffered version of `yip`. Allows up to `n` elements to enqueue on the
   * left unanswered before requiring a response from the right. If buffer is empty,
   * always reads from the left.
   */
  def yipL[I,I2](n: Int): Wye[I,I2,(I,I2)] =
    yipWithL(n)((_,_))

  /** Nondeterministic version of `zipWith` which requests both sides in parallel. */
  def yipWith[I,I2,O](f: (I,I2) => O): Wye[I,I2,O] =
    awaitBoth[I,I2].flatMap {
      case ReceiveL(i) => awaitR[I2].flatMap(i2 => emit(f(i,i2)) ++ yipWith(f))
      case ReceiveR(i2) => awaitL[I].flatMap(i => emit(f(i,i2)) ++ yipWith(f))
      case _ => halt
    }

  /**
   * Left-biased, buffered version of `yipWith`. Allows up to `n` elements to enqueue on the
   * left unanswered before requiring a response from the right. If buffer is empty,
   * always reads from the left.
   */
  def yipWithL[I,O,O2](n: Int)(f: (I,O) => O2): Wye[I,O,O2] = {
    def go(buf: Vector[I]): Wye[I,O,O2] =
      if (buf.size > n) awaitR[O].flatMap { o =>
        emit(f(buf.head,o)) ++ go(buf.tail)
      }
      else if (buf.isEmpty) awaitL[I].flatMap { i => go(buf :+ i) }
      else awaitBoth[I,O].flatMap {
        case ReceiveL(i) => go(buf :+ i)
        case ReceiveR(o) => emit(f(buf.head,o)) ++ go(buf.tail)
        case _ => halt
      }
    go(Vector())
  }




}

object wye extends wye {

  // combinators that don't have globally unique names and
  // shouldn't be mixed into `processes`

  /**
   * Transform the left input of the given `Wye` using a `Process1`.
   */
  def attachL[I0,I,I2,O](p: Process1[I0,I])(w: Wye[I,I2,O]): Wye[I0,I2,O] = w match {
    case h@Halt(_) => h
    case Emit(h,t) => Emit(h, attachL(p)(t))
    case AwaitL(recv, fb, c) =>
      p match {
        case Emit(h, t) => attachL(t)(wye.feedL(h)(w))
        case Await1(recvp, fbp, cp) =>
          await(L[I0]: Env[I0,I2]#Y[I0])(
            recvp andThen (attachL(_)(w)),
            attachL(fbp)(w),
            attachL(cp)(w))
        case h@Halt(_) => attachL(h)(fb)
      }

    case AwaitR(recv, fb, c) =>
      awaitR[I2].flatMap(recv andThen (attachL(p)(_))).
      orElse(attachL(p)(fb), attachL(p)(c))
    case AwaitBoth(recv, fb, c) =>
      p match {
        case Emit(h, t) => attachL(t)(scalaz.stream.wye.feedL(h)(w))
        case Await1(recvp, fbp, cp) =>
          await(Both[I0,I2]: Env[I0,I2]#Y[ReceiveY[I0,I2]])(
            { case ReceiveL(i0) => attachL(p.feed1(i0))(w)
              case ReceiveR(i2) => attachL(p)(feed1R(i2)(w))
              case HaltL(End) => attachL(p.fallback)(w)
              case HaltL(e) => attachL(p.causedBy(e))(haltL(e)(w))
              case HaltR(e) => attachL(p)(haltR(e)(w))
            },
            attachL(fbp)(w),
            attachL(cp)(w))
        case h@Halt(End) => attachL(h)(fb)
        case h@Halt(e) => attachL(h)(c.causedBy(e))
      }
  }

  /**
   * Transform the right input of the given `Wye` using a `Process1`.
   */
  def attachR[I,I1,I2,O](p: Process1[I1,I2])(w: Wye[I,I2,O]): Wye[I,I1,O] =
    flip(attachL(p)(flip(w)))


  /**
   * Feed a single `ReceiveY` value to a `Wye`.
   */
  def feed1[I,I2,O](i: ReceiveY[I,I2])(w: Wye[I,I2,O]): Wye[I,I2,O] =
    i match {
      case ReceiveL(i) => feed1L(i)(w)
      case ReceiveR(i2) => feed1R(i2)(w)
      case HaltL(e) => haltL(e)(w)
      case HaltR(e) => haltR(e)(w)
    }


  /** Feed a single value to the left branch of a `Wye`. */
  def feed1L[I,I2,O](i: I)(w: Wye[I,I2,O]): Wye[I,I2,O] =
    feedL(List(i))(w)

  /** Feed a single value to the right branch of a `Wye`. */
  def feed1R[I,I2,O](i2: I2)(w: Wye[I,I2,O]): Wye[I,I2,O] =
    feedR(List(i2))(w)

  /** Feed a sequence of inputs to the left side of a `Tee`. */
  def feedL[I,I2,O](i: Seq[I])(p: Wye[I,I2,O]): Wye[I,I2,O] = {
    @annotation.tailrec
    def go(in: Seq[I], out: Vector[Seq[O]], cur: Wye[I,I2,O]): Wye[I,I2,O] =
      if (in.nonEmpty) cur match {
        case h@Halt(_) => emitSeq(out.flatten, h)
        case Emit(h, t) => go(in, out :+ h, t)
        case AwaitL(recv, fb, c) =>
          val next =
            try recv(in.head)
            catch {
              case End => fb
              case e: Throwable => c.causedBy(e)
            }
          go(in.tail, out, next)
        case AwaitBoth(recv, fb, c) =>
          val next =
            try recv(ReceiveY.ReceiveL(in.head))
            catch {
              case End => fb
              case e: Throwable => c.causedBy(e)
            }
          go(in.tail, out, next)
        case AwaitR(recv, fb, c) =>
          emitSeq(out.flatten,
          await(R[I2]: Env[I,I2]#Y[I2])(recv andThen (feedL(in)), feedL(in)(fb), feedL(in)(c)))
      }
      else emitSeq(out.flatten, cur)
    go(i, Vector(), p)
  }

  /** Feed a sequence of inputs to the right side of a `Tee`. */
  def feedR[I,I2,O](i: Seq[I2])(p: Wye[I,I2,O]): Wye[I,I2,O] = {

    @annotation.tailrec
    def go(in: Seq[I2], out: Vector[Seq[O]], cur: Wye[I,I2,O]): Wye[I,I2,O] =
      if (in.nonEmpty) cur match {
        case h@Halt(_) => emitSeq(out.flatten, h)
        case Emit(h, t) => go(in, out :+ h, t)
        case AwaitR(recv, fb, c) =>
          val next =
            try recv(in.head)
            catch {
              case End => fb
              case e: Throwable => c.causedBy(e)
            }
          go(in.tail, out, next)
        case AwaitBoth(recv, fb, c) =>
          val next =
            try recv(ReceiveY.ReceiveR(in.head))
            catch {
              case End => fb
              case e: Throwable => c.causedBy(e)
            }
          go(in.tail, out, next)
        case AwaitL(recv, fb, c) =>
          emitSeq(out.flatten,
          await(L[I]: Env[I,I2]#Y[I])(recv andThen (feedR(in)), feedR(in)(fb), feedR(in)(c)))
      }
      else emitSeq(out.flatten, cur)
    go(i, Vector(), p)
  }

  /** Signal to wye that left side has terminated **/
  def haltL[I,I2,O](e:Throwable)(p:Wye[I,I2,O]):Wye[I,I2,O] = {
    p match {
      case h@Halt(_) => h
      case Emit(h, t) =>
        val (nh,nt) = t.unemit
        Emit(h ++ nh, haltL(e)(nt))
      case AwaitL(rcv,fb,c) => p.killBy(e)
      case AwaitR(rcv,fb,c) => await(R[I2]: Env[I,I2]#Y[I2])(rcv, haltL(e)(fb), haltL(e)(c))
      case AwaitBoth(rcv,fb,c) =>
        try rcv(ReceiveY.HaltL(e))
        catch {
          case End => fb
          case e: Throwable =>  c.causedBy(e)
        }
    }
  }
  def haltR[I,I2,O](e:Throwable)(p:Wye[I,I2,O]):Wye[I,I2,O] = {
    p match {
      case h@Halt(_) => h
      case Emit(h, t) =>
        val (nh,nt) = t.unemit
        Emit(h ++ nh, haltR(e)(nt))
      case AwaitR(rcv,fb,c) => p.killBy(e)
      case AwaitL(rcv,fb,c) => await(L[I]: Env[I,I2]#Y[I])(rcv, haltR(e)(fb), haltR(e)(c))
      case AwaitBoth(rcv,fb,c) =>
        try rcv(ReceiveY.HaltR(e))
        catch {
          case End => fb
          case e: Throwable =>  c.causedBy(e)
        }
    }
  }

  /**
   * Convert right requests to left requests and vice versa.
   */
  def flip[I,I2,O](w: Wye[I,I2,O]): Wye[I2,I,O] = w match {
    case h@Halt(_) => h
    case Emit(h, t) => Emit(h, flip(t))
    case AwaitL(recv, fb, c) =>
      await(R[I]: Env[I2,I]#Y[I])(recv andThen (flip), flip(fb), flip(c))
    case AwaitR(recv, fb, c) =>
      await(L[I2]: Env[I2,I]#Y[I2])(recv andThen (flip), flip(fb), flip(c))
    case AwaitBoth(recv, fb, c) =>
      await(Both[I2,I])((t: ReceiveY[I2,I]) => flip(recv(t.flip)), flip(fb), flip(c))
  }

  /**
   * Lift a `Wye` to operate on the left side of an `\/`, passing
   * through any values it receives on the right from either branch.
   */
  def liftL[I0,I,I2,O](w: Wye[I,I2,O]): Wye[I \/ I0, I2 \/ I0, O \/ I0] =
    liftR[I0,I,I2,O](w)
      .map(_.swap)
      .contramapL((e: I \/ I0) => e.swap)
      .contramapR((e: I2 \/ I0) => e.swap)

  /**
   * Lift a `Wye` to operate on the right side of an `\/`, passing
   * through any values it receives on the left from either branch.
   */
  def liftR[I0,I,I2,O](w: Wye[I,I2,O]): Wye[I0 \/ I, I0 \/ I2, I0 \/ O] =
    w match {
      case Emit(h, t) => Emit(h map right, liftR[I0,I,I2,O](t))
      case h@Halt(_) => h
      case AwaitL(recv, fb, c) =>
        val w2: Wye[I0 \/ I, I0 \/ I2, I0 \/ O] =
          awaitL[I0 \/ I].flatMap(_.fold(
            i0 => emit(left(i0)) ++ liftR(w),
            i => liftR[I0,I,I2,O](recv(i))
          ))
        val fb2 = liftR[I0,I,I2,O](fb)
        val c2 = liftR[I0,I,I2,O](c)
        w2.orElse(fb2, c2)
      case AwaitR(recv, fb, c) =>
        val w2: Wye[I0 \/ I, I0 \/ I2, I0 \/ O] =
          awaitR[I0 \/ I2].flatMap(_.fold(
            i0 => emit(left(i0)) ++ liftR(w),
            i => liftR[I0,I,I2,O](recv(i))
          ))
        val fb2 = liftR[I0,I,I2,O](fb)
        val c2 = liftR[I0,I,I2,O](c)
        w2.orElse(fb2, c2)
      case AwaitBoth(recv, fb, c) =>
        val w2: Wye[I0 \/ I, I0 \/ I2, I0 \/ O] = awaitBoth[I0 \/ I, I0 \/ I2].flatMap {
          case ReceiveL(io) => feed1(ReceiveL(io))(liftR(AwaitL(recv compose ReceiveL.apply, fb, c)))
          case ReceiveR(io) => feed1(ReceiveR(io))(liftR(AwaitR(recv compose ReceiveR.apply, fb, c)))
          case HaltL(e) => liftR(w)
          case HaltR(e) => liftR(w)
        }
        val fb2 = liftR[I0,I,I2,O](fb)
        val c2 = liftR[I0,I,I2,O](c)
        w2.orElse(fb2, c2)
    }

  /** Simple enumeration for dynamically generated `Wye` request types. See `wye.dynamic`. */
  trait Request
  object Request {
    case object L extends Request
    case object R extends Request
    case object Both extends Request
  }

  object AwaitL {
    def unapply[I,I2,O](self: Wye[I,I2,O]):
        Option[(I => Wye[I,I2,O], Wye[I,I2,O], Wye[I,I2,O])] = self match {
      case Await(req,recv,fb,c) if req.tag == 0 => Some((recv.asInstanceOf[I => Wye[I,I2,O]], fb, c))
      case _ => None
    }
    def apply[I,I2,O](recv: I => Wye[I,I2,O],
                      fallback: Wye[I,I2,O] = halt,
                      cleanup: Wye[I,I2,O] = halt): Wye[I,I2,O] =
      await(L[I]: Env[I,I2]#Y[I])(recv, fallback, cleanup)
  }
  object AwaitR {
    def unapply[I,I2,O](self: Wye[I,I2,O]):
        Option[(I2 => Wye[I,I2,O], Wye[I,I2,O], Wye[I,I2,O])] = self match {
      case Await(req,recv,fb,c) if req.tag == 1 => Some((recv.asInstanceOf[I2 => Wye[I,I2,O]], fb, c))
      case _ => None
    }
    def apply[I,I2,O](recv: I2 => Wye[I,I2,O],
                      fallback: Wye[I,I2,O] = halt,
                      cleanup: Wye[I,I2,O] = halt): Wye[I,I2,O] =
      await(R[I2]: Env[I,I2]#Y[I2])(recv, fallback, cleanup)
  }
  object AwaitBoth {
    def unapply[I,I2,O](self: Wye[I,I2,O]):
        Option[(ReceiveY[I,I2] => Wye[I,I2,O], Wye[I,I2,O], Wye[I,I2,O])] = self match {
      case Await(req,recv,fb,c) if req.tag == 2 => Some((recv.asInstanceOf[ReceiveY[I,I2] => Wye[I,I2,O]], fb, c))
      case _ => None
    }
    def apply[I,I2,O](recv: ReceiveY[I,I2] => Wye[I,I2,O],
                      fallback: Wye[I,I2,O] = halt,
                      cleanup: Wye[I,I2,O] = halt): Wye[I,I2,O] =
      await(Both[I,I2])(recv, fallback, cleanup)
  }
}
