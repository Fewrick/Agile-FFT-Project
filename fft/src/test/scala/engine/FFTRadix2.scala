package engine

import scala.math._

class FFTRadix2 {
  def fft(x : Seq[Int]) : (Seq[Int], Seq[Int]) = {
    val N = x.length
    require(N > 0 && (N & (N - 1)) == 0, "FFT input length must be a power of 2")

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
