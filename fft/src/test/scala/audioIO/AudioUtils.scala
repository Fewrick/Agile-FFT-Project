package audioIO

class AudioUtils {
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
