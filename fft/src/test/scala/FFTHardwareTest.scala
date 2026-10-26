package fft

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class FFTHardwareTest extends AnyFlatSpec with ChiselScalatestTester {

  // A software reference model mirroring our fixed-point scaling and logic
  def softwareFFT(input: Seq[Int], N: Int): (Seq[Double], Seq[Double]) = {
    if (N == 1) {
      (Seq(input(0).toDouble), Seq(0.0))
    } else {
      val scale = 127.0
      val evensIn = input.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)
      val oddsIn = input.zipWithIndex.filter(_._2 % 2 == 1).map(_._1)
      
      val (evenRe, evenIm) = softwareFFT(evensIn, N / 2)
      val (oddRe, oddIm) = softwareFFT(oddsIn, N / 2)

      val outRe = Array.fill(N)(0.0)
      val outIm = Array.fill(N)(0.0)

      for (k <- 0 until N / 2) {
        val angle = 2 * math.Pi * k / N
        val cosFactor = math.round(math.cos(angle) * scale)
        val sinFactor = math.round(-math.sin(angle) * scale)

        // Mirroring the fixed-point arithmetic
        val tr = (cosFactor * oddRe(k) - sinFactor * oddIm(k)) / scale
        val ti = (cosFactor * oddIm(k) + sinFactor * oddRe(k)) / scale

        outRe(k)       = evenRe(k) + tr
        outIm(k)       = evenIm(k) + ti
        outRe(k + N/2) = evenRe(k) - tr
        outIm(k + N/2) = evenIm(k) - ti
      }
      (outRe.toSeq, outIm.toSeq)
    }
  }

  behavior.of("FFT Hardware Module")

  it("should correctly compute FFT for a DC input (all ones)") {
    val N = 32
    test(new FFT(N)) { c =>
      // 1. Drive inputs (all 1s)
      for (i <- 0 until N) {
        c.io.input(i).poke(1.S)
      }

      // 2. Since it's combinational, values propagate immediately.
      // For DC input, bin 0 should be N (scaled), and others 0.
      for (i <- 0 until N) {
        val re = c.io.outputRe(i).peek().litValue
        val im = c.io.outputIm(i).peek().litValue
        
        if (i == 0) {
          assert(re == N.toLong, s"DC bin 0 real expected $N, got $re")
        } else {
          assert(re == 0L, s"DC bin $i real expected 0, got $re")
        }
        assert(im == 0L, s"DC bin $i imaginary expected 0, got $im")
      }
    }
  }

  it("should match the software reference model for random inputs") {
    val N = 32
    val rand = new Random(42) // Fixed seed for reproducibility

    test(new FFT(N)) { c =>
      // Generate random 8-bit signed inputs (-128 to 127)
      val inputVec = Seq.fill(N)(rand.nextInt(128) - 64)

      // Poke inputs into hardware
      for (i <- 0 until N) {
        c.io.input(i).poke(inputVec(i).S)
      }

      // Compute expected outputs using software golden model
      val (expRe, expIm) = softwareFFT(inputVec, N)

      // Verify each output bin against the golden model (allowing small rounding delta)
      for (i <- 0 until N) {
        val hwRe = c.io.outputRe(i).peek().litValue.toDouble
        val hwIm = c.io.outputIm(i).peek().litValue.toDouble

        // Allow a delta of +/- 2 due to fixed-point rounding accumulation
        assert(math.abs(hwRe - expRe(i)) <= 2.0, f"Bin $i Real mismatch: HW=$hwRe, Expected=${expRe(i)}")
        assert(math.abs(hwIm - expIm(i)) <= 2.0, f"Bin $i Imag mismatch: HW=$hwIm, Expected=${expIm(i)}")
      }
    }
  }
}