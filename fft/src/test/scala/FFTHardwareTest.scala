package fft

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random
import engine.FFTRadix2

class FFTHardwareTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior.of("FFT Hardware Module")

  it should "correctly compute FFT for a DC input (all ones)" in {
    val N = 32
    test(new FFTComb(N)) { c =>
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

  it should "match the software reference model for random inputs" in {
    val N = 32
    val rand = new Random(42) // Fixed seed for reproducibility

    test(new FFTComb(N)) { c =>
      // Generate random 8-bit signed inputs (-128 to 127)
      val inputVec = Seq.fill(N)(rand.nextInt(128) - 64)

      // Poke inputs into hardware
      for (i <- 0 until N) {
        c.io.input(i).poke(inputVec(i).S)
      }

      // Compute expected outputs using software golden model
      val (expRe, expIm) = new FFTRadix2().fft(inputVec)

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

  it should "compute the inverse FFT of a DC input" in {
    val N = 32

    test(new IFFTComb(N)) { c =>
      for (i <- 0 until N) {
        c.io.inputRe(i).poke((if (i == 0) N * 127 else 0).S)
        c.io.inputIm(i).poke(0.S)
      }

      for (i <- 0 until N) {
        assert(c.io.outputRe(i).peek().litValue == 127L, s"Sample $i real mismatch")
        assert(c.io.outputIm(i).peek().litValue == 0L, s"Sample $i imaginary mismatch")
      }
    }
  }

  it should "match the software inverse FFT for random frequency bins" in {
    val N = 32
    val rand = new Random(42)
    val inputRe = Seq.fill(N)(rand.nextInt(128) - 64)
    val inputIm = Seq.fill(N)(rand.nextInt(128) - 64)
    val (expectedRe, expectedIm) = new FFTRadix2().ifftComplex(inputRe, inputIm)

    test(new IFFTComb(N)) { c =>
      for (i <- 0 until N) {
        c.io.inputRe(i).poke(inputRe(i).S)
        c.io.inputIm(i).poke(inputIm(i).S)
      }

      for (i <- 0 until N) {
        val actualRe = c.io.outputRe(i).peek().litValue.toInt
        val actualIm = c.io.outputIm(i).peek().litValue.toInt

        assert(math.abs(actualRe - expectedRe(i)) <= 2, s"Sample $i real mismatch")
        assert(math.abs(actualIm - expectedIm(i)) <= 2, s"Sample $i imaginary mismatch")
      }
    }
  }
}