package top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cosimulation.FFTRadix2
import interfaces.ComplexSample
import scala.util.Random

class FFTDspTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "FFTDsp"

  it should "write, process, and read back random 32-sample inputs" in {
    val samples = 32
    val inputWidth = 8
    val iterations = 10
    val maxCycles = 10000

    // Fixed seed for reproducible tests
    val random = new Random(42)

    test(new FFTDsp(inputWidth, samples)) { dut =>

      // Initialize inputs
      dut.io.writeEn.poke(false.B)
      dut.io.writeAddr.poke(0.U)
      dut.io.writeData.poke(0.S)
      dut.io.start.poke(false.B)
      dut.io.readEn.poke(false.B)
      dut.io.readAddr.poke(0.U)

      dut.clock.step()

      for (iteration <- 0 until iterations) {

        // Generate random signed 8-bit samples [-127, 127]
        val inputData = Seq.fill(samples)(random.nextInt(255) - 127)

        // Compute reference FFT
        val (expectedRe, expectedIm) = FFTRadix2.fft(inputData)

        // Write samples into input SRAM
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
        var cycles = 0

        while (!dut.io.done.peek().litToBoolean && cycles < maxCycles) {
          dut.clock.step()
          cycles += 1
        }

        withClue(s"Iteration $iteration: DSP did not complete within $maxCycles cycles") {
          dut.io.done.expect(true.B)
        }

        dut.io.busy.expect(false.B)

        // Read back and verify output SRAM
        for (i <- 0 until samples) {
          dut.io.readEn.poke(true.B)
          dut.io.readAddr.poke(i.U)

          // SyncReadMem has one-cycle read latency
          dut.clock.step()

          withClue(s"Iteration $iteration, sample $i: ") {
            dut.io.readData.re.expect(expectedRe(i).S)
            dut.io.readData.im.expect(expectedIm(i).S)
          }
        }

        dut.io.readEn.poke(false.B)
        dut.clock.step()
      }
    }
  }
}