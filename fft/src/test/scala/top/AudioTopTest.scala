package top

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import cosimulation.{ComplexValue, FFTRadix2}
import _root_.util.FrequencyMaskReference.maskIndex
import _root_.util.SignalTestUtils.wrapSigned

// Test-only observations and idle inspection reuse the production RAM read ports.
class ObservedAudioTop extends AudioTop(32, 16, 8, 12500, 128, bands = 16) {
  val debug = IO(new Bundle {
    val inspect = Input(Bool())
    val inspectAddr = Input(UInt(recordingAddrWidth.W))
    val originalData = Output(SInt(8.W))
    val processedData = Output(SInt(8.W))
    val idle = Output(Bool())
    val processingCycles = Output(UInt(32.W))
    val counts = Output(Vec(4, UInt(16.W)))
    val captured = Output(Vec(24, SInt(8.W)))
    val processed = Output(Vec(24, SInt(8.W)))
    val played = Output(Vec(24, SInt(8.W)))
  })
  when(debug.inspect) {
    assert(state === State.Idle)
    recordingReadEn := true.B
    recordingReadAddr := debug.inspectAddr
    processedReadEn := true.B
    processedReadAddr := debug.inspectAddr
  }
  debug.originalData := recordingReadData
  debug.processedData := processedReadData
  val counts = RegInit(VecInit(Seq.fill(4)(0.U(16.W))))
  val captured = Reg(Vec(24, SInt(8.W)))
  val processed = Reg(Vec(24, SInt(8.W)))
  val played = Reg(Vec(24, SInt(8.W)))
  val processingCycles = RegInit(0.U(32.W))
  when(acceptProcess) {
    processingCycles := 0.U
  }.elsewhen(state === State.LoadFrame || state === State.Launch ||
    state === State.WaitForDsp || state === State.StoreFrame || state === State.ProcessingDone) {
    processingCycles := processingCycles + 1.U
  }
  debug.processingCycles := processingCycles
  when(hasProcessed) {
    assert(counts(2) === recordingSamples.U, "Processed status requires the complete recording")
  }
  val loadedAddr = RegEnable(loader.io.readAddr, loader.io.readEn)
  when(loader.io.writeEn) {
    assert(loader.io.writeData === captured(loadedAddr), "Every frame must load the original samples")
  }
  when(acceptStart) { counts.foreach(_ := 0.U) }
  when(acceptProcess) { counts(1) := 0.U; counts(2) := 0.U }
  when(acceptOutput) { counts(3) := 0.U }
  when(writeSample) {
    assert(recordingAddr === counts(0))
    captured(recordingAddr) := mic.io.writeData
    counts(0) := counts(0) + 1.U
  }
  when(dsp.io.start) { counts(1) := counts(1) + 1.U }
  when(store.io.writeEn) {
    assert(store.io.writeAddr === counts(2))
    processed(store.io.writeAddr) := store.io.writeData
    counts(2) := counts(2) + 1.U
  }
  val playbackValid = RegNext(speaker.io.readEn, false.B)
  val playbackAddr = RegEnable(speaker.io.readAddr, speaker.io.readEn)
  when(playbackValid) {
    assert(playbackAddr === counts(3))
    played(playbackAddr) := speaker.io.readData
    counts(3) := counts(3) + 1.U
  }
  debug.idle := state === State.Idle
  debug.counts := counts
  debug.captured := captured
  debug.processed := processed
  debug.played := played
}

class AudioTopTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "retain two independent ReadFirst recording RAMs with one read and write port each" in {
    for ((n, length) <- Seq((2, 2), (8, 24), (32, 12512))) {
      val rtl = (new chisel3.stage.ChiselStage).emitChirrtl(
        new AudioTop(n, n/2, 8, 12500, length, bands = 16),
        Array("--target-dir", s"test_run_dir/audio_memory_$length"))
      val top = rtl.substring(rtl.indexOf("  module AudioTop :"))
      val memories = "(?m)^\\s*smem .*".r.findAllIn(top).toSeq
      assert(memories.size == 2, "Only the two recording-sized RAMs belong in AudioTop")
      for (name <- Seq("recordingMem", "processedMem")) {
        assert(memories.exists(_.contains(s"smem $name : SInt<8> [$length] old")))
        for (access <- Seq("read", "write")) {
          val ports = s"$access mport [^=]+ = $name\\[".r.findAllIn(top).size
          assert(ports == 1, s"$name has $ports $access ports")
        }
      }
    }
  }

  it should "preserve originals and independently reprocess and replay complete multi-frame recordings" in {
    test(new ObservedAudioTop).withAnnotations(Seq(VerilatorBackendAnnotation)) { d =>
      d.clock.setTimeout(0)
      d.io.start.poke(false.B); d.io.process.poke(false.B); d.io.output.poke(false.B)
      d.io.streamIn.poke(false.B); d.io.mask.poke(0.U)
      d.debug.inspect.poke(false.B); d.debug.inspectAddr.poke(0.U)
      d.io.streamOut.expect(0.U)

      def press(button: Bool): Unit = {
        button.poke(true.B); d.clock.step(5); button.poke(false.B)
      }
      def expectCounts(values: Int*): Unit = {
        values.zipWithIndex.foreach { case (n, i) => d.debug.counts(i).expect(n.U) }
      }
      def finish(session: Int, limit: Int): Int = {
        var elapsed = 0
        while (!d.debug.idle.peek().litToBoolean && elapsed < limit) {
          // Deterministic PDM density changes produce distinct, nonconstant PCM frames.
          d.io.streamIn.poke(((elapsed / 1000 + session) % 3 != 0).B)
          d.clock.step(250); elapsed += 250
        }
        assert(elapsed < limit, "Operation timed out")
        d.debug.idle.expect(true.B)
        d.clock.step(5)
        elapsed
      }
      def inspect(original: Seq[Int], processed: Option[Seq[Int]] = None): Unit = {
        d.debug.inspect.poke(true.B)
        for (i <- original.indices) {
          d.debug.inspectAddr.poke(i.U)
          d.clock.step() // Existing synchronous RAM latency, with no extra port.
          d.debug.originalData.expect(original(i).S)
          processed.foreach(v => d.debug.processedData.expect(v(i).S))
        }
        d.debug.inspect.poke(false.B)
        d.clock.step()
      }
      def reference(input: Seq[Int], mask: Int): Seq[Int] = {
        input.grouped(d.samples).flatMap { frame =>
          val filtered = FFTRadix2.fft(frame).zipWithIndex.map { case (bin, k) =>
            if (((mask >> maskIndex(k, d.samples, d.bands)) & 1) != 0) bin
            else ComplexValue(0, 0)
          }
          FFTRadix2.ifft(filtered).map(wrapSigned(_, 8))
        }.toSeq
      }
      def playback(session: Int, expected: Seq[Int], processed: Boolean): Unit = {
        press(d.io.output)
        d.io.streamOut.expect((if (processed) 7 else 5).U)
        // Busy edges must not queue another operation or invalidate the result.
        press(d.io.start); press(d.io.process); press(d.io.output)
        finish(session, 220000)
        d.io.streamOut.expect((if (processed) 3 else 1).U)
        expected.zipWithIndex.foreach { case (v, i) => d.debug.played(i).expect(v.S) }
        d.debug.counts(3).expect(d.recordingSamples.U)
      }

      // No operations are accepted before the first capture.
      press(d.io.output); d.clock.step(5)
      press(d.io.process); d.clock.step(5)
      d.debug.idle.expect(true.B); expectCounts(0, 0, 0, 0)
      d.io.streamOut.expect(0.U)
      var previousInput = Seq.empty[Int]
      for (session <- 0 until 2) {
        // Simultaneous presses prioritize capture over process and playback.
        d.io.process.poke(true.B); d.io.output.poke(true.B)
        press(d.io.start)
        d.io.process.poke(false.B); d.io.output.poke(false.B)
        d.io.streamOut.expect(0.U)
        d.clock.step(5)
        press(d.io.start); press(d.io.process); press(d.io.output)
        finish(session, 220000)
        expectCounts(24, 0, 0, 0)
        d.io.streamOut.expect(1.U)
        d.clock.step(1000); expectCounts(24, 0, 0, 0)
        val input = d.debug.captured.map(_.peek().litValue.toInt).toSeq
        assert(input.distinct.size > 2, "Capture must exercise nontrivial samples")
        if (previousInput.nonEmpty) assert(input != previousInput)
        previousInput = input
        inspect(input)
        playback(session, input, processed = false)
        inspect(input)

        def process(mask: Int, hold: Boolean = false): (Seq[Int], Int) = {
          d.io.mask.poke(mask.U)
          // Process beats a simultaneous playback request.
          d.io.output.poke(true.B)
          d.io.process.poke(true.B); d.clock.step(5)
          d.io.output.poke(false.B)
          d.io.streamOut.expect(1.U)
          // The registered mask must survive changes while processing is busy.
          d.io.mask.poke((mask ^ 65535).U)
          if (!hold) { d.io.process.poke(false.B); d.clock.step(5) }
          press(d.io.start); press(d.io.output)
          if (!hold) { press(d.io.process) }
          finish(session, 20000)
          // A held process button must not restart processing on returning to Idle.
          d.clock.step(1000)
          expectCounts(24, 3, 24, 24)
          d.io.streamOut.expect(3.U)
          d.io.process.poke(false.B); d.clock.step(5)
          val result = d.debug.processed.map(_.peek().litValue.toInt).toSeq
          assert(result == reference(input, mask), "Must match DSP reference from the original")
          inspect(input, Some(result))
          (result, d.debug.processingCycles.peek().litValue.toInt)
        }

        val (firstA, latency) = process(0x8000, hold = true)
        info(s"Session $session: $latency processing cycles for ${d.frames} frames")
        playback(session, firstA, processed = true)
        val (secondA, secondLatency) = process(0x8000)
        assert(secondLatency == latency)
        assert(secondA == firstA, "Repeated processing must be bit-identical")
        val (zero, _) = process(0)
        assert(zero.forall(_ == 0))
        playback(session, zero, processed = true)
        val (allPass, _) = process(0xffff)
        assert(allPass.grouped(d.samples).exists { frame =>
          FFTRadix2.fft(frame).drop(1).exists(bin => bin.re != 0 || bin.im != 0)
        }, "Non-DC frequencies removed by the zero mask must be recoverable")
        // Compare to the fixed-point reference, never assume lossless all-pass output.
        assert(allPass == reference(input, 0xffff))
        for (_ <- 0 until 2) { playback(session, allPass, processed = true) }
        val (finalA, finalLatency) = process(0x8000, hold = true)
        assert(finalA == firstA, "A -> B -> A must restore the identical masked result")
        assert(finalLatency == latency, "Mask changes must preserve processing throughput")
        playback(session, finalA, processed = true)
        inspect(input, Some(finalA))
      }
    }
  }
}
