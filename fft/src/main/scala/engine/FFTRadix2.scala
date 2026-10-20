package engine

import scala.math._

/*
 * TODO: Define fixed-point quantization and scaling.
 *
 * Input samples are represented as signed 8-bit integers using
 * symmetric quantization:
 *   Floating-point range: [-1.0, 1.0]
 *   Integer range:        [-127, 127]
 *   Quantization scale:   127
 *
 * The forward FFT is unnormalized, so its output magnitude
 * can grow by a factor of N. Intermediate values therefore
 * require additional bit width or per-stage scaling.
 *
 * Currently, twiddle factors and butterfly calculations use
 * floating-point arithmetic, with results rounded to Int.
 * For hardware verification, define a fixed-point format
 * and consistent rounding/overflow behavior.
 */

class FFTRadix2 {
  private def validateLength(n: Int): Unit = {
    require(n > 0 && (n & (n - 1)) == 0, "FFT length must be a positive power of 2")
  }

  def fft(x : Seq[Int]) : (Seq[Int], Seq[Int]) = {
    val N = x.length
    validateLength(N)

    // Base case
    if (N == 1) {
      (Seq(x.head), Seq(0))
    } else {
      // Split input into even and odd indexed samples
      val even = x.indices.collect { case i if i % 2 == 0 => x(i) }
      val odd = x.indices.collect { case i if i % 2 == 1 => x(i) }

      // Recursive FFTs
      val (evenRe, evenIm) = fft(even)
      val (oddRe, oddIm) = fft(odd)

      val XRe = new Array[Int](N)
      val XIm = new Array[Int](N)

      for (k <- 0 until N / 2) {
        val angle = 2 * Pi * k / N
        val twiddleRe = cos(angle)
        val twiddleIm = -sin(angle)

        val tempRe = twiddleRe * oddRe(k) - twiddleIm * oddIm(k)
        val tempIm = twiddleRe * oddIm(k) + twiddleIm * oddRe(k)

        XRe(k) = (evenRe(k) + tempRe).round.toInt
        XIm(k) = (evenIm(k) + tempIm).round.toInt

        XRe(k + N/2) = (evenRe(k) - tempRe).round.toInt
        XIm(k + N/2) = (evenIm(k) - tempIm).round.toInt
      }
      (XRe, XIm)
    }
  }

  def ifft(XRe: Seq[Int], XIm: Seq[Int]): (Seq[Int], Seq[Int])  = {
    require(XRe.length == XIm.length, "Number of real and imaginary elements should be equal")
    val N = XRe.length
    validateLength(N)

    // Base case
    if (N == 1) {
      (Seq(XRe.head), Seq(XIm.head))
    } else {
      // Split frequency bins into even and odd indices
      val evenRe = XRe.indices.collect { case i if i % 2 == 0 => XRe(i) }
      val evenIm = XIm.indices.collect { case i if i % 2 == 0 => XIm(i) }
      val oddRe  = XRe.indices.collect { case i if i % 2 == 1 => XRe(i) }
      val oddIm  = XIm.indices.collect { case i if i % 2 == 1 => XIm(i) }

      // Recursive IFFTs
      val (eRe, eIm) = ifft(evenRe, evenIm)
      val (oRe, oIm) = ifft(oddRe, oddIm)

      val xRe = new Array[Int](N)
      val xIm = new Array[Int](N)

      for (k <- 0 until N / 2) {
        val angle = 2*Pi*k/N

        // Inversed sign (compared to FFT)
        val twiddleRe = cos(angle)
        val twiddleIm = sin(angle)

        val tempRe = twiddleRe * oRe(k) - twiddleIm * oIm(k)
        val tempIm = twiddleRe * oIm(k) + twiddleIm * oRe(k)

        // Divide by 2 at every radix-2 stage.
        // Across log2(N) stages this gives the required 1/N.
        xRe(k) = ((eRe(k) + tempRe) / 2.0).round.toInt
        xIm(k) = ((eIm(k) + tempIm) / 2.0).round.toInt

        xRe(k+N/2) = ((eRe(k) - tempRe) / 2.0).round.toInt
        xIm(k+N/2) = ((eIm(k) - tempIm) / 2.0).round.toInt
      }
      (xRe, xIm)
    }
  }
}
