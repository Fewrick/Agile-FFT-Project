
package top

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import cosimulation.{FFTRadix2, ComplexValue}
import scala.util.Random

class FFTDspTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "FFTDsp"

  val samples = 32
  val inputWidth = 8
  val maxCycles = 10000
  val random = new Random(42)

  // --------------------------------------------------
  // Software reference for the frequency mask
  // --------------------------------------------------

  def applyMask(fft: Seq[ComplexValue], mask: Int): Seq[ComplexValue] = {
    require(fft.length == samples)
    fft.zipWithIndex.map { case (bin, k) =>
      val maskIndex = math.min(k, samples - 1 - k)
      val enabled = ((mask >> maskIndex) & 1) == 1
      if (enabled) bin else ComplexValue(0, 0)
    }
  }

  // --------------------------------------------------
  // Complete software DSP reference
  // --------------------------------------------------

  def referenceDSP(input: Seq[Int], mask: Int): Seq[Int] = {
    val fft = FFTRadix2.fft(input)
    val filtered = applyMask(fft, mask)
    val ifft = FFTRadix2.ifft(filtered)
    ifft
  }

  // --------------------------------------------------
  // Wrap overflow values
  // --------------------------------------------------

  def wrapSigned(value: Int, width: Int): Int = {
    val modulus = 1L << width
    val unsigned = ((value.toLong % modulus) + modulus) % modulus
    val signBit = 1L << (width - 1)

    if (unsigned >= signBit) {
      (unsigned - modulus).toInt
    } else {
      unsigned.toInt
    }
  }

  // --------------------------------------------------
  // Hardware test
  // --------------------------------------------------

  it should "correctly perform FFT, filtering and IFFT" in {

    test(new FFTDsp(inputWidth, samples))
      .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>

      // Initialize inputs
      dut.io.writeEn.poke(false.B)
      dut.io.writeAddr.poke(0.U)
      dut.io.writeData.poke(0.S)

      dut.io.start.poke(false.B)
      dut.io.readEn.poke(false.B)
      dut.io.readAddr.poke(0.U)

      dut.io.mask.poke(0.U)

      dut.clock.step()

      // Test masks
      val masks = Seq(
        0xFFFF, // All bins enabled
        0x0000, // All bins disabled
        0x0001, // First mirrored pair
        0x8000, // Last mirrored pair
        0x00FF, // Lower 8 mask bits enabled
        0xFF00, // Upper 8 mask bits enabled
        0xAAAA, // Alternating
        0x5555, // Alternating inverse
        0x0F0F, // Groups
        0xF0F0
      ) ++ Seq.fill(10)(random.nextInt(1 << 16))

      for ((mask, iteration) <- masks.zipWithIndex) {

        // Signed 8-bit random input
        val inputData = Seq.fill(samples)(random.nextInt(256) - 128)

        // Expected output from software model
        val expected = referenceDSP(inputData, mask).map(v => wrapSigned(v, inputWidth))

        // -------------------------
        // Write input samples
        // -------------------------

        for (i <- 0 until samples) {
          dut.io.writeEn.poke(true.B)
          dut.io.writeAddr.poke(i.U)
          dut.io.writeData.poke(inputData(i).S)
          dut.clock.step()
        }

        dut.io.writeEn.poke(false.B)

        // -------------------------
        // Start DSP
        // -------------------------

        dut.io.mask.poke(mask.U)

        dut.io.start.poke(true.B)
        dut.clock.step()
        dut.io.start.poke(false.B)

        // -------------------------
        // Wait for completion
        // -------------------------

        var cycles = 0

        while (!dut.io.done.peek().litToBoolean && cycles < maxCycles) {
          dut.clock.step()
          cycles += 1
        }

        withClue(s"Iteration $iteration, mask 0x${mask.toHexString}: ") {
          dut.io.done.expect(true.B)
        }

        dut.io.busy.expect(false.B)

        // -------------------------
        // Read and verify output
        // -------------------------

        for (i <- 0 until samples) {
          dut.io.readEn.poke(true.B)
          dut.io.readAddr.poke(i.U)

          // SyncReadMem read latency
          dut.clock.step()

          val actual = dut.io.readData.peek().litValue.toInt

          withClue(s"Iteration $iteration, " + s"mask 0x${mask.toHexString}, " + s"sample $i: ") {
            actual shouldBe expected(i)
          }
        }

        dut.io.readEn.poke(false.B)
        dut.clock.step()
      }
    }
  }
}
