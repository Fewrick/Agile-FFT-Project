package fft

import chisel3._

class DFT extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(32, UInt(8.W)))
    val outputRe = Output(Vec(32, UInt(8.W)))
    val outputIm = Output(Vec(32, UInt(8.W)))
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
    io.outputRe(k) 
    // Average instead of magnitude
    val posCosSum = Mux(cosSum(15), -cosSum, cosSum)
    val posSinSum = Mux(sinSum(15), -sinSum, sinSum)
    val magnitude = (posCosSum + posSinSum)(15,9)
    io.outputRe(k) := magnitude
    io.outputIm(k) := magnitude
  }
}
