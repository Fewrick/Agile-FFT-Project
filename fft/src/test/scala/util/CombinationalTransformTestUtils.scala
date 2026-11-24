package util

import chisel3._
import chiseltest._
import interfaces.ComplexSample

object CombinationalTransformTestUtils {
  def displayExample(input: Vec[SInt], output: Vec[ComplexSample], samples: Seq[Int]): Unit = {
    var sampleString = "samples: "
    for (i <- input.indices) {
      input(i).poke(samples(i).S)
      sampleString += s"${samples(i)} "
    }
    println(sampleString)
    var freqString = "freqs:   "
    for (i <- output.indices) {
      freqString += s"${output(i).re.peek().litValue.toInt}/"
      freqString += s"${output(i).im.peek().litValue.toInt} "
    }
    println(freqString)
  }
}
