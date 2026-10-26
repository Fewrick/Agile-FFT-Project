package engine

import scala.math._

/*
 * Radix-2 FFT/IFFT using fixed-point integer arithmetic.
 *
 * Input:
 *   Signed 8-bit samples in [-128, 127].
 *   Quantization scale: 127.
 *
 * Twiddle factors:
 *   cos/sin are quantized to [-127, 127] using Math.round (floor(v + 0.5)).
 *   Complex multiplication is rescaled by dividing by 127.
 *
 * FFT:
 *   Unnormalized forward transform.
 *   Intermediate values may exceed the 8-bit input range.
 *
 * IFFT:
 *   Normalized inverse transform.
 *   Division by 2 at each radix-2 stage gives 1/N scaling.
 *
 * Arithmetic:
 *   Integer butterfly operations with Long multiplication
 *   intermediates.
 *   Division truncates toward zero.
 */

class FFTRadix2 {
  val SCALE = 127

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
        val twiddleRe = (cos(angle)  * SCALE).round.toInt
        val twiddleIm = (-sin(angle) * SCALE).round.toInt

        val tempRe = ((twiddleRe.toLong * oddRe(k) - twiddleIm.toLong * oddIm(k)) / SCALE).toInt
        val tempIm = ((twiddleRe.toLong * oddIm(k) + twiddleIm.toLong * oddRe(k)) / SCALE).toInt

        XRe(k) = (evenRe(k) + tempRe)
        XIm(k) = (evenIm(k) + tempIm)

        XRe(k + N/2) = (evenRe(k) - tempRe)
        XIm(k + N/2) = (evenIm(k) - tempIm)
      }
      (XRe, XIm)
    }
  }

  def ifft(XRe: Seq[Int], XIm: Seq[Int]): Seq[Int]  = {
    ifftComplex(XRe, XIm)._1
  }

  def ifftComplex(XRe: Seq[Int], XIm: Seq[Int]): (Seq[Int], Seq[Int])  = {
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
      val (eRe, eIm) = ifftComplex(evenRe, evenIm)
      val (oRe, oIm) = ifftComplex(oddRe, oddIm)

      val xRe = new Array[Int](N)
      val xIm = new Array[Int](N)

      for (k <- 0 until N / 2) {
        val angle = 2*Pi*k/N

        // Inversed sign (compared to FFT)
        val twiddleRe = (cos(angle) * SCALE).round.toInt
        val twiddleIm = (sin(angle) * SCALE).round.toInt

        val tempRe = ((twiddleRe.toLong * oRe(k) - twiddleIm.toLong * oIm(k)) / 127).toInt
        val tempIm = ((twiddleRe.toLong * oIm(k) + twiddleIm.toLong * oRe(k)) / 127).toInt

        // Divide by 2 at every radix-2 stage.
        // Across log2(N) stages this gives the required 1/N.
        xIm(k) = ((eIm(k) + tempIm) / 2)
        xRe(k) = ((eRe(k) + tempRe) / 2)

        xRe(k+N/2) = ((eRe(k) - tempRe) / 2)
        xIm(k+N/2) = ((eIm(k) - tempIm) / 2)
      }
      (xRe, xIm)
    }
  }
}
