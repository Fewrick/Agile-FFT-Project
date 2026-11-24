
package filter

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class SimpleBandpassTest extends AnyFlatSpec with ChiselScalatestTester {
  val samples = 32
  val half = samples / 2

  // Same mapping as in BandpassFilter: FFT index -> switch number
  def switchFor(i: Int): Int =
    math.min(15, math.min(i, samples - i) * 16 / half)

  "BandpassFilter" should "filter frequencies based on the mask" in {
    test(new BandpassFilter(8, samples)) { dut =>

      def run(mask: Int): Unit = {
        println(s"Testing with mask 0b${mask.toBinaryString.reverse.padTo(16, '0').reverse}")
        dut.io.mask.poke(mask.U)
        for (i <- 0 until samples) {
          val re = Random.nextInt(256) - 128
          val im = Random.nextInt(256) - 128
          dut.io.freqIndex.poke(i.U)
          dut.io.in.re.poke(re.S)
          dut.io.in.im.poke(im.S)
          dut.clock.step()

          val pass = ((mask >> switchFor(i)) & 1) == 1
          val (expRe, expIm) = if (pass) (re, im) else (0, 0)
          println(s"idx $i (switch ${switchFor(i)}): in=($re,$im) " +
            s"out=(${dut.io.out.re.peek().litValue},${dut.io.out.im.peek().litValue})")
          dut.io.out.re.expect(expRe.S)
          dut.io.out.im.expect(expIm.S)
        }
      }

      run(0x0000)                  // all switches off
      run(0xFFFF)                  // all switches on
      (0 until 16).foreach(bit => run(1 << bit))
      run(Random.nextInt(65536))   // random mask
    }
  }
}
