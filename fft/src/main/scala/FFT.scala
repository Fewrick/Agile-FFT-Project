package fft

import chisel3._

class FFT extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(32, UInt(8.W)))
    val output = Output(Vec(32, UInt(8.W)))
  })

  val N = 32

  for (k <- 0 until N) {
    val cosSum = (0 until N).map(n => {
      val cosFactor = (math.cos(2 * math.Pi * k * n / N) * 127).intValue.S(8.W)
      val product = io.input(n) * cosFactor
      product
    }).reduce((a, b) => a + b)
    val sinSum = (0 until N).map(n => {
      val sinFactor = (math.sin(2 * math.Pi * k * n / N) * 127).intValue.S(8.W)
      val product = io.input(n) * sinFactor
      product
    }).reduce((a, b) => a + b)
    // Average instead of magnitude
    val posCosSum = Mux(cosSum(15), -cosSum, cosSum)
    val posSinSum = Mux(sinSum(15), -sinSum, sinSum)
    val magnitude = (posCosSum + posSinSum)(15,9)
    io.output(k) := magnitude
  }
}

object FFT extends App {
  emitVerilog(new FFT())
}
