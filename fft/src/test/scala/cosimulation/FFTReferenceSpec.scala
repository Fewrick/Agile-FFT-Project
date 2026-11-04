
package cosimulation

import org.scalatest.funsuite.AnyFunSuite

class FFTReferenceSpec extends AnyFunSuite {

  private def assertClose(actual: Seq[Double], expected: Seq[Double], tolerance: Double = 1e-10): Unit = {
    assert(actual.length == expected.length)
    actual.zip(expected).foreach { case (a, e) => assert(math.abs(a - e) < tolerance) }
  }

  test("DFT produces the correct spectrum") {
    val input = Seq(0.0, 1.0, 0.0, 0.0)

    val (re, im) = FFTFP.fft(input)

    assertClose(re, Seq(1.0, 0.0, -1.0, 0.0))
    assertClose(im, Seq(0.0, -1.0, 0.0, 1.0))
  }

  test("DFT followed by IDFT reconstructs input") {
    val input = Seq(1.0, 2.0, 3.0, 4.0)

    val (re, im) = FFTFP.fft(input)
    val reconstructed = FFTFP.ifft(re, im)

    assertClose(reconstructed, input)
  }

  test("Radix-2 FFT produces the correct spectrum") {
    val input = Seq(1, 2, 3, 4)

    val (re, im) = FFTRadix2.fft(input)

    assert(re == Seq(10, -2, -2, -2))
    assert(im == Seq(0, 2, 0, -2))
  }

  test("Radix-2 IFFT reconstructs a known input") {
    val input = Seq(1, 2, 3, 4)

    val (re, im) = FFTRadix2.fft(input)
    val output = FFTRadix2.ifft(re, im)

    assert(output == input)
  }

  test("Radix-2 FFT rejects non-power-of-two inputs") {
    intercept[IllegalArgumentException] {
      FFTRadix2.fft(Seq(1, 2, 3))
    }
  }

  test("Radix-2 FFT display") {
    val input = Seq(127, 64, 0, -64, -127, -64, 0, 64)

    val (freqRe, freqIm) = FFTRadix2.fft(input)
    val output = FFTRadix2.ifft(freqRe, freqIm)

    info(s"Original:      ${input.mkString(", ")}")
    info(s"Reconstructed: ${output.mkString(", ")}")
  }
}
