package util

object FrequencyMaskReference {
  // Match BandpassFilter's current pairing of bin k with N-1-k.
  def maskIndex(bin: Int, samples: Int, bands: Int): Int = {
    val mirroredBin = math.min(bin, samples - 1 - bin)
    val d = math.log(samples / 2) / math.log(2) / bands
    val cutoffs = (0 until bands).map(i => math.pow(2.0, i * d))
    val band = (0 until bands - 1).find { i =>
      mirroredBin + 1 >= cutoffs(i) && mirroredBin + 1 < cutoffs(i + 1)
    }
    band.getOrElse(bands - 1)
  }
}
