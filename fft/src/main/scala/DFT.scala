package fft

import chisel3._

class DFT(val N: Int) extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(32, UInt(8.W)))
    val outputRe = Output(Vec(32, UInt(8.W)))
    val outputIm = Output(Vec(32, UInt(8.W)))
  })

  for (k <- 0 until N) {
    io.outputRe(k) := (0 until N).map(n => {
      val cosFactor = (math.cos(2 * math.Pi * k * n / N) * 127).intValue.S(8.W)
      val product = io.input(n) * cosFactor
      product
    }).reduce((a, b) => a + b)(14,8)
    io.outputIm(k) := (0 until N).map(n => {
      val sinFactor = (math.sin(2 * math.Pi * k * n / N) * 127).intValue.S(8.W)
      val product = io.input(n) * sinFactor
      product
    }).reduce((a, b) => a + b)(14,8)
  }
}
