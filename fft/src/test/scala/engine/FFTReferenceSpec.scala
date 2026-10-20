
package engine

import org.scalatest.funsuite.AnyFunSuite

class FFTReferenceSpec extends AnyFunSuite {

  private val fp = new FFTFP
  private val radix2 = new FFTRadix2

  private def assertClose(actual: Seq[Double], expected: Seq[Double], tolerance: Double = 1e-10): Unit = {
    assert(actual.length == expected.length)
    actual.zip(expected).foreach { case (a, e) => assert(math.abs(a - e) < tolerance) }
  }

  test("DFT produces the correct spectrum") {
    val input = Seq(0.0, 1.0, 0.0, 0.0)

    val (re, im) = fp.fft(input)

    assertClose(re, Seq(1.0, 0.0, -1.0, 0.0))
    assertClose(im, Seq(0.0, -1.0, 0.0, 1.0))
  }

  test("DFT followed by IDFT reconstructs input") {
    val input = Seq(1.0, 2.0, 3.0, 4.0)

    val (re, im) = fp.fft(input)
    val reconstructed = fp.ifft(re, im)

    assertClose(reconstructed, input)
  }

  test("Radix-2 FFT produces the correct spectrum") {
    val input = Seq(1, 2, 3, 4)

    val (re, im) = radix2.fft(input)

    assert(re == Seq(10, -2, -2, -2))
    assert(im == Seq(0, 2, 0, -2))
  }

  test("Radix-2 IFFT reconstructs a known input") {
    val input = Seq(1, 2, 3, 4)

    val (re, im) = radix2.fft(input)
    val output = radix2.ifft(re, im)

    assert(output == input)
  }

  test("Radix-2 FFT rejects non-power-of-two inputs") {
    intercept[IllegalArgumentException] {
      radix2.fft(Seq(1, 2, 3))
    }
  }

  test("Radix-2 FFT display") {
    val input = Seq(127, 64, 0, -64, -127, -64, 0, 64)

    val (freqRe, freqIm) = radix2.fft(input)
    val output = radix2.ifft(freqRe, freqIm)

    info(s"Original:      ${input.mkString(", ")}")
    info(s"Reconstructed: ${output.mkString(", ")}")
  }
}
