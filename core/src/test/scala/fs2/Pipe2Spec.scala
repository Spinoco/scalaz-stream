package fs2

import fs2.util.Task
import org.scalacheck.Gen

class Pipe2Spec extends Fs2Spec {

  "Pipe2" - {

    "zipWith left/right side infinite" in {
      val ones = Stream.constant("1")
      val p = Stream("A","B","C")
      runLog(ones.zipWith(p)(_ + _)) shouldBe Vector("1A", "1B", "1C")
      runLog(p.zipWith(ones)(_ + _)) shouldBe Vector("A1", "B1", "C1")
    }

    "zipWith both side infinite" in {
      val ones = Stream.constant("1")
      val as = Stream.constant("A")
      runLog(ones.zipWith(as)(_ + _).take(3)) shouldBe Vector("1A", "1A", "1A")
      runLog(as.zipWith(ones)(_ + _).take(3)) shouldBe Vector("A1", "A1", "A1")
    }

    "zipAllWith left/right side infinite" in {
      val ones = Stream.constant("1")
      val p = Stream("A","B","C")
      runLog(ones.through2p(p)(pipe2.zipAllWith("2","Z")(_ + _)).take(5)) shouldBe
          Vector("1A", "1B", "1C", "1Z", "1Z")
      runLog(p.through2p(ones)(pipe2.zipAllWith("Z","2")(_ + _)).take(5)) shouldBe
        Vector("A1", "B1", "C1", "Z1", "Z1")
    }

    "zipAllWith both side infinite" in {
      val ones = Stream.constant("1")
      val as = Stream.constant("A")
      runLog(ones.through2p(as)(pipe2.zipAllWith("2", "Z")(_ + _)).take(3)) shouldBe
       Vector("1A", "1A", "1A")
      runLog(as.through2p(ones)(pipe2.zipAllWith("Z", "2")(_ + _)).take(3)) shouldBe
       Vector("A1", "A1", "A1")
    }

    "zip left/right side infinite" in {
      val ones = Stream.constant("1")
      val p = Stream("A","B","C")
      runLog(ones.zip(p)) shouldBe Vector("1" -> "A", "1" -> "B", "1" -> "C")
      runLog(p.zip(ones)) shouldBe Vector("A" -> "1", "B" -> "1", "C" -> "1")
    }

    "zip both side infinite" in {
      val ones = Stream.constant("1")
      val as = Stream.constant("A")
      runLog(ones.zip(as).take(3)) shouldBe Vector("1" -> "A", "1" -> "A", "1" -> "A")
      runLog(as.zip(ones).take(3)) shouldBe Vector("A" -> "1", "A" -> "1", "A" -> "1")
    }

    "zipAll left/right side infinite" in {
      val ones = Stream.constant("1")
      val p = Stream("A","B","C")
      runLog(ones.through2p(p)(pipe2.zipAll("2","Z")).take(5)) shouldBe Vector("1" -> "A", "1" -> "B", "1" -> "C", "1" -> "Z", "1" -> "Z")
      runLog(p.through2p(ones)(pipe2.zipAll("Z","2")).take(5)) shouldBe Vector("A" -> "1", "B" -> "1", "C" -> "1", "Z" -> "1", "Z" -> "1")
    }

    "zipAll both side infinite" in {
      val ones = Stream.constant("1")
      val as = Stream.constant("A")
      runLog(ones.through2p(as)(pipe2.zipAll("2", "Z")).take(3)) shouldBe Vector("1" -> "A", "1" -> "A", "1" -> "A")
      runLog(as.through2p(ones)(pipe2.zipAll("Z", "2")).take(3)) shouldBe Vector("A" -> "1", "A" -> "1", "A" -> "1")
    }

    "interleave left/right side infinite" in {
      val ones = Stream.constant("1")
      val p = Stream("A","B","C")
      runLog(ones.interleave(p)) shouldBe Vector("1", "A", "1", "B", "1", "C")
      runLog(p.interleave(ones)) shouldBe Vector("A", "1", "B", "1", "C", "1")
    }

    "interleave both side infinite" in {
      val ones = Stream.constant("1")
      val as = Stream.constant("A")
      runLog(ones.interleave(as).take(3)) shouldBe Vector("1", "A", "1")
      runLog(as.interleave(ones).take(3)) shouldBe Vector("A", "1", "A")
    }

    "interleaveAll left/right side infinite" in {
      val ones = Stream.constant("1")
      val p = Stream("A","B","C")
      runLog(ones.interleaveAll(p).take(9)) shouldBe Vector("1", "A", "1", "B", "1", "C", "1", "1", "1")
      runLog(p.interleaveAll(ones).take(9)) shouldBe Vector("A", "1", "B", "1", "C", "1", "1", "1", "1")
    }

    "interleaveAll both side infinite" in {
      val ones = Stream.constant("1")
      val as = Stream.constant("A")
      runLog(ones.interleaveAll(as).take(3)) shouldBe Vector("1", "A", "1")
      runLog(as.interleaveAll(ones).take(3)) shouldBe Vector("A", "1", "A")
    }

    // Uses a small scope to avoid using time to generate too large streams and not finishing
    "interleave is equal to interleaveAll on infinite streams (by step-indexing)" in {
      forAll(Gen.choose(0,100)) { (n : Int) =>
        val ones = Stream.constant("1")
        val as = Stream.constant("A")
        ones.interleaveAll(as).take(n).toVector shouldBe ones.interleave(as).take(n).toVector
      }
    }

    "either" in forAll { (s1: PureStream[Int], s2: PureStream[Int]) =>
      val shouldCompile = s1.get.either(s2.get.covary[Task])
      val es = runLog { s1.get.covary[Task].through2(s2.get)(pipe2.either) }
      es.collect { case Left(i) => i } shouldBe runLog(s1.get)
      es.collect { case Right(i) => i } shouldBe runLog(s2.get)
    }

    "merge" in forAll { (s1: PureStream[Int], s2: PureStream[Int]) =>
      runLog { s1.get.merge(s2.get.covary[Task]) }.toSet shouldBe
      (runLog(s1.get).toSet ++ runLog(s2.get).toSet)
    }

    "merge (left/right identity)" in forAll { (s1: PureStream[Int]) =>
      runLog { s1.get.merge(Stream.empty.covary[Task]) } shouldBe runLog(s1.get)
      runLog { Stream.empty.through2(s1.get.covary[Task])(pipe2.merge) } shouldBe runLog(s1.get)
    }

    "merge (left/right failure)" in forAll { (s1: PureStream[Int], f: Failure) =>
      an[Err.type] should be thrownBy {
        (s1.get merge f.get).run.run.unsafeRun
      }
    }

    "mergeHalt{L/R/Both}" in forAll { (s1: PureStream[Int], s2: PureStream[Int]) =>
      withClue(s1.tag + " " + s2.tag) {
        val outBoth = runLog { s1.get.covary[Task].map(Left(_)) mergeHaltBoth s2.get.map(Right(_)) }
        val outL = runLog { s1.get.covary[Task].map(Left(_)) mergeHaltL s2.get.map(Right(_)) }
        val outR = runLog { s1.get.covary[Task].map(Left(_)) mergeHaltR s2.get.map(Right(_)) }
        // out should contain at least all the elements from one of the input streams
        val e1 = runLog(s1.get)
        val e2 = runLog(s2.get)
        assert {
          (outBoth.collect { case Left(a) => a } == e1) ||
          (outBoth.collect { case Right(a) => a } == e2)
        }
        outL.collect { case Left(a) => a } shouldBe e1
        outR.collect { case Right(a) => a } shouldBe e2
      }
    }

    "interrupt (1)" in forAll { (s1: PureStream[Int]) =>
      val s = async.mutable.Semaphore[Task](0).unsafeRun
      val interrupt = Stream.emit(true) ++ Stream.eval_(s.increment)
      // tests that termination is successful even if stream being interrupted is hung
      runLog { s1.get.evalMap(_ => s.decrement).interruptWhen(interrupt) } shouldBe Vector()
      // tests that termination is successful even if interruption stream is infinitely false
      runLog { s1.get.covary[Task].interruptWhen(Stream.constant(false)) } shouldBe runLog(s1.get)
    }

    "interrupt (2)" in forAll { (s1: PureStream[Int]) =>
      val barrier = async.mutable.Semaphore[Task](0).unsafeRun
      val enableInterrupt = async.mutable.Semaphore[Task](0).unsafeRun
      val interruptedS1 = s1.get.evalMap { i =>
        // enable interruption and hang when hitting a value divisible by 7
        if (i % 7 == 0) enableInterrupt.increment.flatMap { _ => barrier.decrement.map(_ => i) }
        else Task.now(i)
      }
      val interrupt = Stream.eval(enableInterrupt.decrement) flatMap { _ => Stream.emit(false) }
      val out = runLog { interruptedS1.interruptWhen(interrupt) }
      // as soon as we hit a value divisible by 7, we enable interruption then hang before emitting it,
      // so there should be no elements in the output that are divisible by 7
      // this also checks that interruption works fine even if one or both streams are in a hung state
      assert(out.forall(i => i % 7 != 0))
    }
  }
}
