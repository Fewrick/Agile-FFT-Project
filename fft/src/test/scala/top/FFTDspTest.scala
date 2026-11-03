package top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cosimulation.FFTRadix2
import interfaces.ComplexSample

class FFTDspTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "FFTDsp"

  it should "write, process, and read back 32 samples" in {
    val samples = 32
    val inputWidth = 8
    val inputData = (0 until samples).map(i => i - 16)

    val (expectedRe, expectedIm) = FFTRadix2.fft(inputData)

    test(new FFTDsp(inputWidth, samples)) { dut =>

      // Initialize inputs
      dut.io.writeEn.poke(false.B)
      dut.io.writeAddr.poke(0.U)
      dut.io.writeData.poke(0.S)
      dut.io.start.poke(false.B)
      dut.io.readEn.poke(false.B)
      dut.io.readAddr.poke(0.U)

      dut.clock.step()

      // Write 32 samples into input SRAM
      for (i <- 0 until samples) {
        dut.io.writeEn.poke(true.B)
        dut.io.writeAddr.poke(i.U)
        dut.io.writeData.poke(inputData(i).S)
        dut.clock.step()
      }

      dut.io.writeEn.poke(false.B)

      // Start DSP processing
      dut.io.start.poke(true.B)
      dut.clock.step()
      dut.io.start.poke(false.B)

      // Wait for completion with timeout
      val maxCycles = 10000
      var cycles = 0

      while (!dut.io.done.peek().litToBoolean && cycles < maxCycles) {
        dut.clock.step()
        cycles += 1
      }

      withClue(s"DSP did not complete within $maxCycles cycles") {
        dut.io.done.expect(true.B)
      }

      dut.io.busy.expect(false.B)

      // Read back 32 samples from output SRAM
      for (i <- 0 until samples) {
        dut.io.readEn.poke(true.B)
        dut.io.readAddr.poke(i.U)

        // SyncReadMem has one-cycle read latency
        dut.clock.step()

        dut.io.readData.re.expect(expectedRe(i).S)
        dut.io.readData.im.expect(expectedIm(i).S)
      }

      dut.io.readEn.poke(false.B)
    }
  }
}