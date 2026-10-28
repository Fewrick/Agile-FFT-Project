package fft

import chisel3._
import chisel3.util.log2Ceil

class FFTComb(val N: Int = 32) extends Module {
  require(N > 0 && (N & (N - 1)) == 0, "N must be a positive power of 2")

  val SCALE = 127
  val inputWidth = 8
  val outputWidth = inputWidth + log2Ceil(N)

  val io = IO(new Bundle {
    val input = Input(Vec(N, SInt(8.W)))
    val outputRe = Output(Vec(N, SInt(outputWidth.W)))
    val outputIm = Output(Vec(N, SInt(outputWidth.W)))
  })
  if (N == 1) {
    io.outputRe(0) := io.input(0)
    io.outputIm(0) := 0.S
  } else {
    val evens = Module(new FFTComb(N / 2))
    val odds = Module(new FFTComb(N / 2))

    evens.io.input := io.input.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)
    odds.io.input := io.input.zipWithIndex.filter(_._2 % 2 == 1).map(_._1)

    for (k <- 0 until N / 2) {
      val angle = 2 * math.Pi * k / N

      val cosFactor = math.round(math.cos(angle) * SCALE).toInt.S(8.W)
      val sinFactor = math.round(-math.sin(angle) * SCALE).toInt.S(8.W)

      val tr = ((cosFactor * odds.io.outputRe(k)) -& (sinFactor * odds.io.outputIm(k))) / SCALE.S(8.W)
      val ti = ((cosFactor * odds.io.outputIm(k)) +& (sinFactor * odds.io.outputRe(k))) / SCALE.S(8.W)

      io.outputRe(k) := evens.io.outputRe(k) +& tr
      io.outputIm(k) := evens.io.outputIm(k) +& ti

      io.outputRe(k + N / 2) := evens.io.outputRe(k) -& tr
      io.outputIm(k + N / 2) := evens.io.outputIm(k) -& ti
    }
  }
}

object FFTComb extends App {
  emitVerilog(new FFTComb())
}
