package fft

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import fft.DFT
import scala.util.Random
import _root_.util.CombinationalTransformTestUtils.displayExample

class DFTTest extends AnyFlatSpec with ChiselScalatestTester {
  "DFT" should "display example" in {
    test(new DFT(32)) { dut =>
      println("Example 1 (constant):")
      displayExample(dut.io.input, dut.io.output, (0 until 32).map(i => 40))

      println("Example 2: (linear)")
      displayExample(dut.io.input, dut.io.output, 0 until 32)

      println("Example 3: (sine)")
      displayExample(dut.io.input, dut.io.output, (0 until 32).map(i => (math.sin(i)*127).intValue))

      println("Example 3: (noise)")
      displayExample(dut.io.input, dut.io.output, (0 until 32).map(i => (Random.nextInt(256) - 128)))
    }
  }

}
