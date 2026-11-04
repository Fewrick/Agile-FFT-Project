package fft

import chisel3._
import chisel3.util.log2Ceil
import interfaces.ComplexSample

class FFTComb(val N: Int = 32) extends Module {
  require(N > 0 && (N & (N - 1)) == 0, "N must be a positive power of 2")

  val SCALE = 127
  val inputWidth = 8
  val outputWidth = inputWidth + log2Ceil(N)

  val io = IO(new Bundle {
    val input = Input(Vec(N, SInt(8.W)))
    val output = Output(Vec(N, new ComplexSample(outputWidth)))
  })
  if (N == 1) {
    io.output(0).re := io.input(0)
    io.output(0).im := 0.S
  } else {
    val evens = Module(new FFTComb(N / 2))
    val odds = Module(new FFTComb(N / 2))

    evens.io.input := io.input.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)
    odds.io.input := io.input.zipWithIndex.filter(_._2 % 2 == 1).map(_._1)

    for (k <- 0 until N / 2) {
      val angle = 2 * math.Pi * k / N

      val cosFactor = math.round(math.cos(angle) * SCALE).toInt.S(8.W)
      val sinFactor = math.round(-math.sin(angle) * SCALE).toInt.S(8.W)

      val tr = ((cosFactor * odds.io.output(k).re) -& (sinFactor * odds.io.output(k).im)) / SCALE.S(8.W)
      val ti = ((cosFactor * odds.io.output(k).im) +& (sinFactor * odds.io.output(k).re)) / SCALE.S(8.W)

      io.output(k).re := evens.io.output(k).re +& tr
      io.output(k).im := evens.io.output(k).im +& ti

      io.output(k + N / 2).re := evens.io.output(k).re -& tr
      io.output(k + N / 2).im := evens.io.output(k).im -& ti
    }
  }
}

object FFTComb extends App {
  emitVerilog(new FFTComb())
}
