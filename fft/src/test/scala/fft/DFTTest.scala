package fft

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import fft.DFT
import scala.util.Random

class DFTTest extends AnyFlatSpec with ChiselScalatestTester {
  "DFT" should "display example" in {
    test(new DFT(32)) { dut =>
      println("Example 1 (constant):")
      example(dut, (0 until 32).map(i => 40))

      println("Example 2: (linear)")
      example(dut, 0 until 32)

      println("Example 3: (sine)")
      example(dut, (0 until 32).map(i => (math.sin(i)*127).intValue))

      println("Example 3: (noise)")
      example(dut, (0 until 32).map(i => (Random.nextInt(256) - 128)))
    }
  }

  def example(dut: DFT, seq: Seq[Int]) {
    var sampleString = "samples: "
    for (i <- 0 until 32) {
      dut.io.input(i).poke(seq(i).S)
      sampleString += s"${seq(i)} "
    }
    println(sampleString)
    var freqString = "freqs:   "
    for (i <- 0 until 32) {
      freqString += s"${dut.io.output(i).re.peek().litValue.toInt}/"
      freqString += s"${dut.io.output(i).im.peek().litValue.toInt} "
    }
    println(freqString)
  }
}
