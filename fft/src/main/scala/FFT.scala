package fft

import chisel3._

class FFT(val N: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(N, UInt(8.W)))
    val outputRe = Output(Vec(N, UInt(8.W)))
    val outputIm = Output(Vec(N, UInt(8.W)))
  })
  // make sure that N > 0 and N is a power of 2
  require(N > 0, "N must be greater than 0")
  require((N & (N - 1)) == 0, "N must be a power of 2")

  // the alorithm
  if (N==1) {
    io.outputRe := io.input
    io.outputIm := io.input
  } else {
    val evens = Module(new FFT(N/2))
    val odds = Module(new FFT(N/2))

    evens.io.input := io.input.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)
    odds.io.input := io.input.zipWithIndex.filter(_._2 % 2 == 1).map(_._1)

    for (k <- 0 until N/2) {
      val cosFactor = (math.cos(2 * math.Pi * k * n / N) * 127).intValue.S(8.W)
      val sinFactor = (math.sin(2 * math.Pi * k * n / N) * 127).intValue.S(8.W)

      // currently scaled to 8 bit by simply removing the least significant bits
      // (14,7) is presumably incorrect, and needs to be changed
      io.outputRe(k) := evens.io.outputRe(k) + (cosFactor * odds.io.outputRe(k) - sinFactor * odds.io.outputIm(k))(14,7) 
      io.outputIm(k) := evens.io.outputIm(k) + (cosFactor * odds.io.outputIm(k) + sinFactor * odds.io.outputRe(k))(14,7)
      
      io.outputRe(k + N/2) := evens.io.outputRe(k) - (cosFactor * odds.io.outputRe(k) - sinFactor * odds.io.outputIm(k))(14,7)
      io.outputIm(k + N/2) := evens.io.outputIm(k) - (cosFactor * odds.io.outputIm(k) + sinFactor * odds.io.outputRe(k))(14,7)
    }
  }
}

object FFT extends App {
  emitVerilog(new FFT())
}
