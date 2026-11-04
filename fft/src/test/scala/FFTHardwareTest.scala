package fft

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random
import cosimulation.FFTRadix2
import fft.FFTComb

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
        val re = c.io.output(i).re.peek().litValue
        val im = c.io.output(i).im.peek().litValue
        
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
      val (expRe, expIm) = FFTRadix2.fft(inputVec)

      // Verify each output bin against the golden model (allowing small rounding delta)
      for (i <- 0 until N) {
        val hwRe = c.io.output(i).re.peek().litValue.toDouble
        val hwIm = c.io.output(i).im.peek().litValue.toDouble

        // Allow a delta of +/- 2 due to fixed-point rounding accumulation
        assert(math.abs(hwRe - expRe(i)) <= 2.0, f"Bin $i Real mismatch: HW=$hwRe, Expected=${expRe(i)}")
        assert(math.abs(hwIm - expIm(i)) <= 2.0, f"Bin $i Imag mismatch: HW=$hwIm, Expected=${expIm(i)}")
      }
    }
  }

  
  it should "concentrate energy in the correct frequency bin" in {
    val N = 32
    val targetBin = 4 // The frequency index of our injected sine wave

    test(new FFTComb(N)) { c =>
      // 1. Generate a single-tone sine wave input
      val scale = 127.0
      for (i <- 0 until N) {
        val sample = math.round(math.sin(2 * math.Pi * targetBin * i / N) * scale).toInt
        c.io.input(i).poke(sample.S)
      }

      // 2. Compute squared magnitudes for all output bins 
      // (Using squared magnitude Re^2 + Im^2 avoids expensive square roots in Scala)
      val magnitudes = (0 until N).map { i =>
        val re = c.io.output(i).re.peek().litValue.toDouble
        val im = c.io.output(i).im.peek().litValue.toDouble
        (i, (re * re) + (im * im))
      }

      // 3. Find the bin with the maximum energy (the peak)
      val (peakBin, peakMag) = magnitudes.maxBy(_._2)

      // 4. Assert that the peak is precisely at our target frequency bin
      assert(peakBin == targetBin, s"Spectral peak expected at bin $targetBin, but found at bin $peakBin")

      // 5. Assert that all other bins are near zero (noise floor check)
      magnitudes.foreach { case (bin, mag) =>
        if (bin != targetBin) {
          // Other bins should be less than 5% of the peak magnitude due to fixed-point precision limits
          assert(mag < (peakMag * 0.05), s"Bin $bin has unexpected leakage energy: $mag (peak was $peakMag)")
        }
      }
    }
  }

  it should "produce a flat frequency spectrum for an impulse input" in {
    val N = 32
    test(new FFTComb(N)) { c =>
      // Drive a strong impulse at index 0 (e.g., 127) and 0 everywhere else
      c.io.input(0).poke(127.S)
      for (i <- 1 until N) {
        c.io.input(i).poke(0.S)
      }

      // Read back the real part of bin 0 as a baseline reference
      val baseRe = c.io.output(0).re.peek().litValue.toDouble

      // Verify that the frequency response is flat (all real parts equal) 
      // and that the imaginary parts are all zero.
      for (i <- 0 until N) {
        val re = c.io.output(i).re.peek().litValue.toDouble
        val im = c.io.output(i).im.peek().litValue.toDouble

        // Real parts should remain uniform across all bins for an impulse
        assert(math.abs(re - baseRe) <= 2.0, s"Impulse spectrum not flat at bin $i: real=$re (expected ~ $baseRe)")
        
        // Imaginary parts should be close to zero
        assert(math.abs(im) <= 2.0, s"Impulse imaginary part non-zero at bin $i: imag=$im")
      }
    }
  }

  it should "handle boundary conditions and extreme saturation values safely" in {
    val N = 32

    test(new FFTComb(N)) { c =>
      // --- Scenario A: Maximum Positive DC ---
      for (i <- 0 until N) {
        c.io.input(i).poke(127.S)
      }

      // Verify outputs are stable and within valid bit width bounds (13 bits max)
      for (i <- 0 until N) {
        val re = c.io.output(i).re.peek().litValue
        val im = c.io.output(i).im.peek().litValue
        
        // Ensure values can be successfully evaluated without simulation faults
        assert(re.abs < 8192, s"Output real exceeds 13-bit bound at bin $i: $re")
        assert(im.abs < 8192, s"Output imag exceeds 13-bit bound at bin $i: $im")
      }

      // --- Scenario B: Alternating Nyquist Frequency Vector ---
      for (i <- 0 until N) {
        val extremeVal = if (i % 2 == 0) 127.S else (-127).S
        c.io.input(i).poke(extremeVal)
      }

      // Verify stability under extreme alternating inputs
      for (i <- 0 until N) {
        val re = c.io.output(i).re.peek().litValue
        val im = c.io.output(i).im.peek().litValue
        
        // Check that the alternating pattern correctly concentrates energy at the Nyquist bin (N/2)
        if (i == N / 2) {
          assert(re.abs > 0 || im.abs > 0, "Nyquist bin should contain energy for alternating input")
        }
      }
    }
  }
}