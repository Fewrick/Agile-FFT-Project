package fft

import chisel3._
import chisel3.util.log2Ceil

class DFT(val N: Int = 32) extends Module {
  require(N > 0 && (N & (N - 1)) == 0)

  val inputWidth = 8
  val outputWidth = inputWidth + log2Ceil(N)
  val accumulatorWidth = 2 * inputWidth + log2Ceil(N)
  val SCALE = 127

  val io = IO(new Bundle {
    val input = Input(Vec(N, SInt(inputWidth.W)))
    val outputRe = Output(Vec(N, SInt(outputWidth.W)))
    val outputIm = Output(Vec(N, SInt(outputWidth.W)))
  })

  for (k <- 0 until N) {
    val cosSum = (0 until N).map { n =>
      val angle = 2 * math.Pi * k * n / N
      val cosFactor = math.round(math.cos(angle) * SCALE).toInt.S(8.W)
      (io.input(n) * cosFactor).pad(accumulatorWidth)
    }.reduce(_ + _)
    val sinSum = (0 until N).map { n =>
      val angle = 2 * math.Pi * k * n / N
      val sinFactor = math.round(-math.sin(angle) * SCALE).toInt.S(8.W)
      (io.input(n) * sinFactor).pad(accumulatorWidth)
    }.reduce(_ + _)
    io.outputRe(k) := cosSum / SCALE.S(8.W)
    io.outputIm(k) := sinSum / SCALE.S(8.W)
  }
}