package util

object FrequencyMaskReference {
  // Match BandpassFilter's current pairing of bin k with N-1-k.
  def maskIndex(bin: Int, samples: Int): Int =
    math.min(bin, samples - 1 - bin) * 16 / (samples / 2)
}
