package fft

import chisel3._
import chisel3.util.log2Ceil

class IFFTComb(
    val N: Int,
    val outputWidth: Int,
    val internalWidth: Int
) extends Module {
  def this(N: Int) = this(N, 8, 8 + log2Ceil(N))
  def this() = this(32)

  require(N > 0 && (N & (N - 1)) == 0, "N must be a positive power of 2")
  require(outputWidth > 0, "outputWidth must be positive")
  require(internalWidth >= outputWidth, "internalWidth must not be smaller than outputWidth")

  val SCALE = 127
  val inputWidth = internalWidth

  val io = IO(new Bundle {
    val inputRe = Input(Vec(N, SInt(inputWidth.W)))
    val inputIm = Input(Vec(N, SInt(inputWidth.W)))
    val outputRe = Output(Vec(N, SInt(outputWidth.W)))
    val outputIm = Output(Vec(N, SInt(outputWidth.W)))
  })

  if (N == 1) {
    io.outputRe(0) := io.inputRe(0)
    io.outputIm(0) := io.inputIm(0)
  } else {
    val evens = Module(new IFFTComb(N / 2, internalWidth, internalWidth))
    val odds = Module(new IFFTComb(N / 2, internalWidth, internalWidth))

    evens.io.inputRe := io.inputRe.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)
    evens.io.inputIm := io.inputIm.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)
    odds.io.inputRe := io.inputRe.zipWithIndex.filter(_._2 % 2 == 1).map(_._1)
    odds.io.inputIm := io.inputIm.zipWithIndex.filter(_._2 % 2 == 1).map(_._1)

    for (k <- 0 until N / 2) {
      val angle = 2 * math.Pi * k / N

      val cosFactor = math.round(math.cos(angle) * SCALE).toInt.S(8.W)
      val sinFactor = math.round(math.sin(angle) * SCALE).toInt.S(8.W)

      val tr = ((cosFactor * odds.io.outputRe(k)) -& (sinFactor * odds.io.outputIm(k))) /
        SCALE.S(8.W)
      val ti = ((cosFactor * odds.io.outputIm(k)) +& (sinFactor * odds.io.outputRe(k))) /
        SCALE.S(8.W)

      io.outputRe(k) := (evens.io.outputRe(k) +& tr) / 2.S
      io.outputIm(k) := (evens.io.outputIm(k) +& ti) / 2.S

      io.outputRe(k + N / 2) := (evens.io.outputRe(k) -& tr) / 2.S
      io.outputIm(k + N / 2) := (evens.io.outputIm(k) -& ti) / 2.S
    }
  }
}

object IFFTComb extends App {
  emitVerilog(new IFFTComb())
}
