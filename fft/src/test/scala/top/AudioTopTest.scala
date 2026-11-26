package top

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import cosimulation.FFTRadix2
import _root_.util.SignalTestUtils.wrapSigned

// Test-only observations: production IO and recording memory remain unchanged.
class ObservedAudioTop extends AudioTop(4, 8, 12500, 8, bands = 16) {
  val debug = IO(new Bundle {
    val idle = Output(Bool())
    val counts = Output(Vec(4, UInt(16.W)))
    val captured = Output(Vec(8, SInt(8.W)))
    val processed = Output(Vec(8, SInt(8.W)))
    val played = Output(Vec(8, SInt(8.W)))
  })
  val counts = RegInit(VecInit(Seq.fill(4)(0.U(16.W))))
  val captured = Reg(Vec(8, SInt(8.W)))
  val processed = Reg(Vec(8, SInt(8.W)))
  val played = Reg(Vec(8, SInt(8.W)))
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
    played(playbackAddr) := memReadData
    counts(3) := counts(3) + 1.U
  }
  debug.idle := state === State.Idle
  debug.counts := counts
  debug.captured := captured
  debug.processed := processed
  debug.played := played
}

class AudioTopTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "control recording, processing and replay independently across sessions" in {
    test(new ObservedAudioTop).withAnnotations(Seq(VerilatorBackendAnnotation)) { d =>
      d.clock.setTimeout(0)
      d.io.start.poke(false.B); d.io.process.poke(false.B); d.io.output.poke(false.B)
      d.io.streamIn.poke(false.B); d.io.mask.poke(0.U)
      d.io.streamOut.expect(0.U)

      def press(button: Bool): Unit = {
        button.poke(true.B); d.clock.step(5); button.poke(false.B)
      }
      def expectCounts(values: Int*): Unit = {
        values.zipWithIndex.foreach { case (n, i) => d.debug.counts(i).expect(n.U) }
      }
      def finish(session: Int, limit: Int): Unit = {
        var elapsed = 0
        while (!d.debug.idle.peek().litToBoolean && elapsed < limit) {
          d.io.streamIn.poke(((elapsed / 1000 + session) % 3 != 0).B)
          d.clock.step(250); elapsed += 250
        }
        assert(elapsed < limit, "Operation timed out")
        d.debug.idle.expect(true.B)
        d.clock.step(5)
      }

      // No memory accesses or processing are allowed before the first capture.
      press(d.io.output); d.clock.step(5)
      press(d.io.process); d.clock.step(5)
      d.debug.idle.expect(true.B); expectCounts(0, 0, 0, 0)
      d.io.streamOut.expect(0.U)

      for (session <- 0 until 2) {
        press(d.io.start)
        d.io.streamOut.expect(0.U)
        // Busy commands must be ignored, including another capture request.
        d.clock.step(5)
        press(d.io.start); press(d.io.process); press(d.io.output)
        finish(session, 80000)
        expectCounts(8, 0, 0, 0)
        d.io.streamOut.expect(1.U)
        d.clock.step(1000); expectCounts(8, 0, 0, 0)
        val input = d.debug.captured.map(_.peek().litValue.toInt).toSeq

        // Playback before processing is useful for listening to the raw recording.
        press(d.io.output)
        d.io.streamOut.expect(5.U)
        finish(session, 80000)
        d.io.streamOut.expect(1.U)
        expectCounts(8, 0, 0, 8)
        input.zipWithIndex.foreach { case (v, i) => d.debug.played(i).expect(v.S) }

        d.io.mask.poke(65535.U)
        // Keep process held across completion: it must launch only once.
        d.io.process.poke(true.B); d.clock.step(5)
        d.io.streamOut.expect(1.U)
        d.io.mask.poke(0.U)
        finish(session, 10000)
        d.clock.step(1000); expectCounts(8, 2, 8, 8)
        d.io.streamOut.expect(3.U)
        d.io.process.poke(false.B); d.clock.step(5)
        val expected = input.grouped(4).flatMap { frame =>
          FFTRadix2.ifft(FFTRadix2.fft(frame)).map(wrapSigned(_, 8))
        }.toSeq
        expected.zipWithIndex.foreach { case (v, i) => d.debug.processed(i).expect(v.S) }

        for (_ <- 0 until 2) {
          press(d.io.output)
          d.io.streamOut.expect(7.U)
          finish(session, 80000)
          d.io.streamOut.expect(3.U)
          expectCounts(8, 2, 8, 8)
          expected.zipWithIndex.foreach { case (v, i) => d.debug.played(i).expect(v.S) }
        }
      }
    }
  }
}
