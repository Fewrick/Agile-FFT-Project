package top

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import _root_.util.SignalTestUtils.pcmToPdm

class AudioTopTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "reproduce a constant PCM value with all FFT bins enabled" in {
    val samples = 32
    val expected = 65
    val bitsPerSample = 16 * 10
    val clocksPerBit = 50
    val clocksPerSample = clocksPerBit * bitsPerSample
    val pdm = pcmToPdm(expected, bitsPerSample)

    test(new AudioTop(samples)).withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
      dut.clock.setTimeout(0)
      dut.io.start.poke(false.B)
      dut.io.mask.poke("hffff".U)

      var cycle = 0
      def tick(): Unit = {
        dut.io.streamIn.poke(pdm((cycle / clocksPerBit) % pdm.size).B)
        dut.clock.step()
        cycle += 1
      }

      for (_ <- 0 until 2 * clocksPerSample) {
        tick()
      }
      dut.io.streamOut.expect(0.U)

      dut.io.start.poke(true.B)
      for (_ <- 0 until 4) {
        tick()
      }
      dut.io.start.poke(false.B)

      val deadline = cycle + (samples + 2) * clocksPerSample
      while (cycle < deadline && dut.io.streamOut.peek().litValue == 0) tick()
      assert(dut.io.streamOut.peek().litValue != 0, "Timed out waiting for output")

      for (_ <- 0 until samples) {
        dut.io.streamOut.expect(expected.U)
        tick()
      }
      dut.io.streamOut.expect(0.U)
    }
  }
}