package engine

import scala.math._

class FFTFP {
  def fft(x: Seq[Double]) : (Seq[Double], Seq[Double]) = {
    val N = x.length
    val XRe = new Array[Double](N)
    val XIm = new Array[Double](N)

    for (k <- 0 until N) {
      var sumRe = 0.0
      var sumIm = 0.0
      for (n <- 0 until N) {
        val angle = (2.0*Pi*k*n)/N
        sumRe += x(n) * cos(angle)
        sumIm -= x(n) * sin(angle)
      }
      XRe(k) = sumRe
      XIm(k) = sumIm
    }
    (XRe, XIm)
  }

  def ifft(XRe: Seq[Double], XIm: Seq[Double]) : Seq[Double] = {
    require(XRe.length == XIm.length, "Number of real and imaginary elements should be equal")
    val N = XRe.length
    val x = new Array[Double](N)

    for (n <- 0 until N) {
      var sum = 0.0
      for (k <- 0 until N) {
        val angle = (2.0*Pi*k*n)/N
        sum += XRe(k)*cos(angle) - XIm(k)*sin(angle)
      }
      x(n) = sum/N
    }
    x
  }
}
