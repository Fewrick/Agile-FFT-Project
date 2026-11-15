package cosimulation

import scala.math._

object FFTFP {
  def fft(x: Seq[Double]) : (Seq[ComplexFP]) = {
    require(x.nonEmpty, "DFT input must not be empty")


    val N = x.length
    val X = new Array[ComplexFP](N)

    for (k <- 0 until N) {
      var sum = ComplexFP(0.0, 0.0)
      for (n <- 0 until N) {
        val angle = (2.0*Pi*k*n)/N
        sum = sum + ComplexFP(x(n) * cos(angle), -x(n) * sin(angle))
      }
      X(k) = sum
    }
    X
  }

  def ifft(X: Seq[ComplexFP]) : Seq[Double] = {
    require(X.nonEmpty, "IDFT input must not be empty")

    val N = X.length
    val x = new Array[Double](N)

    for (n <- 0 until N) {
      var sum = 0.0
      for (k <- 0 until N) {
        val angle = (2.0*Pi*k*n)/N
        sum += X(k).re*cos(angle) - X(k).im*sin(angle)
      }
      x(n) = sum/N
    }
    x
  }
}
