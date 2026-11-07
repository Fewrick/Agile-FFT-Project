
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
    if (i < half) (i * 16) / half else ((samples - i - 1) * 16) / half

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
      run(Random.nextInt(65536))   // random mask
    }
  }
}
// package filter

// import chisel3._
// import chiseltest._
// import org.scalatest.flatspec.AnyFlatSpec
// import filter.BandpassFilter
// import scala.util.Random

// class SimpleBandpassTest extends AnyFlatSpec with ChiselScalatestTester {
//   "BandpassFilter" should "filter frequencies based on the mask" in {
//     test(new BandpassFilter(8, 32)) { dut =>
//       // Test case 1: All switches off
//       println("Testing with all switches off (mask 0x0000)")    
//       dut.io.mask.poke(0.U)
//       for (i <- 0 until 32) {
//         println(s"Frequency index: $i")
//         dut.io.freqIndex.poke(i.U)
//         dut.io.in.poke((Random.nextInt(256) - 128).S) // Random input sample
//         dut.clock.step()
        
//         // peek the output and check if it matches the expected behavior based on the mask
//         val outputSample = dut.io.out.peek()
//         println(s"Input sample: ${inputSample.litValue}, Output sample: ${outputSample.litValue}")

//         dut.io.out.expect(0.S) // Expect output to be zero
//       }

//       // Test case 2: All switches on
//       println("Testing with all switches on (mask 0xFFFF)")
//       dut.io.mask.poke("hFFFF".U)
//       for (i <- 0 until 32) {
//         println(s"Frequency index: $i")
//         val inputSample = (Random.nextInt(256) - 128).S
//         dut.io.freqIndex.poke(i.U)
//         dut.io.in.poke(inputSample)
//         dut.clock.step()

//         // peek the output and check if it matches the expected behavior based on the mask
//         val outputSample = dut.io.out.peek()
//         println(s"Input sample: ${inputSample.litValue}, Output sample: ${outputSample.litValue}")

//         dut.io.out.expect(inputSample) // Expect output to match input
//       }

//       // Test case 3: Random mask
//       val randomMask = Random.nextInt(65536).U
//       println(s"Testing with random mask 0b${randomMask.litValue.toBinaryString}")
//       dut.io.mask.poke(randomMask)
//       for (i <- 0 until 32) {
//         println(s"Frequency index: $i ")
//         val inputSample = (Random.nextInt(256) - 128).S
//         dut.io.freqIndex.poke(i.U)
//         dut.io.in.poke(inputSample)
//         dut.clock.step()
        
//         // peek the output and check if it matches the expected behavior based on the mask
//         val outputSample = dut.io.out.peek()
//         println(s"Input sample: ${inputSample.litValue}, Output sample: ${outputSample.litValue}")

//         if ((randomMask.litValue & (1 << i)) != 0) {
//           dut.io.out.expect(inputSample) // Expect output to match input if switch is on
//         } else {
//           dut.io.out.expect(0.S) // Expect output to be zero if switch is off
//         }
//       }
//     }
//   }
// }