package util

object SignalTestUtils {
  // Signed output-width wrapping for scala.
  def wrapSigned(value: Int, width: Int): Int = {
    val mask = (1L << width) - 1
    val bits = value.toLong & mask
    val signBit = 1L << (width - 1)
    (if ((bits & signBit) != 0) bits - (1L << width) else bits).toInt
  }

  def pcmToPdm(sample: Int, bitCount: Int): Vector[Boolean] = {
    require(sample >= -128 && sample <= 127)
    require(bitCount > 0)

    val density = sample + 128
    var accumulator = 128

    Vector.tabulate(bitCount) { _ =>
      accumulator += density
      val bit = accumulator >= 256

      if (bit) {
        accumulator -= 256
      }

      bit
    }
  }
}
