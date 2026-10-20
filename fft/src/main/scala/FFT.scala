package fft

import chisel3._

class FFT extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(32, UInt(8.W)))
    val outputRe = Output(Vec(32, UInt(8.W)))
    val outputIm = Output(Vec(32, UInt(8.W)))
  })

  // Placeholder logic

  io.outputRe := io.input
  io.outputIm := io.input
}

object FFT extends App {
  emitVerilog(new FFT())
}
