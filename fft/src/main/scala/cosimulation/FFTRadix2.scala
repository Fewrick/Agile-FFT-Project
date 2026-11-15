
package cosimulation

import scala.math._

/*
 * Radix-2 FFT/IFFT using fixed-point integer arithmetic.
 *
 * Input:
 *   Signed 8-bit samples in [-128, 127].
 *   Quantization scale: 127.
 *
 * Twiddle factors:
 *   cos/sin are quantized to [-127, 127] using Math.round.
 *   Complex multiplication is rescaled by dividing by 127.
 *
 * FFT:
 *   Unnormalized forward transform.
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

object FFTRadix2 {
  val SCALE = 127

  private def validateLength(n: Int): Unit = {
    require(n > 0 && (n & (n - 1)) == 0, "FFT length must be a positive power of 2")
  }

  // --------------------------------------------------
  // Forward FFT
  // --------------------------------------------------

  def fft(x: Seq[Int]): Seq[ComplexValue] = {
    fftComplex(x.map(v => ComplexValue(v, 0)))
  }

  def fftComplex(x: Seq[ComplexValue]): Seq[ComplexValue] = {
    val N = x.length
    validateLength(N)

    if (N == 1) {
      x
    } else {
      val even = fftComplex(x.indices.collect { case i if i % 2 == 0 => x(i) })
      val odd  = fftComplex(x.indices.collect { case i if i % 2 == 1 => x(i) })

      val output = new Array[ComplexValue](N)

      for (k <- 0 until N / 2) {
        val angle = 2 * Pi * k / N

        val twiddleRe = (cos(angle) * SCALE).round.toInt
        val twiddleIm = (-sin(angle) * SCALE).round.toInt

        val tempRe = ((twiddleRe.toLong * odd(k).re - twiddleIm.toLong * odd(k).im) / SCALE).toInt
        val tempIm = ((twiddleRe.toLong * odd(k).im + twiddleIm.toLong * odd(k).re) / SCALE).toInt

        output(k)         = ComplexValue(even(k).re + tempRe, even(k).im + tempIm)
        output(k + N / 2) = ComplexValue(even(k).re - tempRe, even(k).im - tempIm)
      }
      output.toSeq
    }
  }

  // --------------------------------------------------
  // Inverse FFT
  // --------------------------------------------------

  def ifft(X: Seq[ComplexValue]): Seq[Int] = {
    ifftComplex(X).map(_.re)
  }

  def ifftComplex(X: Seq[ComplexValue]): Seq[ComplexValue] = {
    val N = X.length
    validateLength(N)

    if (N == 1) {
      X
    } else {
      val even = ifftComplex(X.indices.collect { case i if i % 2 == 0 => X(i) })
      val odd  = ifftComplex(X.indices.collect { case i if i % 2 == 1 => X(i) })

      val output = new Array[ComplexValue](N)

      for (k <- 0 until N / 2) {
        val angle = 2 * Pi * k / N

        // Inverse FFT uses positive sine
        val twiddleRe = (cos(angle) * SCALE).round.toInt
        val twiddleIm = (sin(angle) * SCALE).round.toInt

        val tempRe = ((twiddleRe.toLong * odd(k).re - twiddleIm.toLong * odd(k).im) / SCALE).toInt
        val tempIm = ((twiddleRe.toLong * odd(k).im + twiddleIm.toLong * odd(k).re) / SCALE).toInt

        // Normalize by 2 at every radix-2 stage
        output(k) = ComplexValue((even(k).re + tempRe) / 2, (even(k).im + tempIm) / 2)

        output(k + N / 2) = ComplexValue((even(k).re - tempRe) / 2, (even(k).im - tempIm) / 2)
      }
      output.toSeq
    }
  }
}
