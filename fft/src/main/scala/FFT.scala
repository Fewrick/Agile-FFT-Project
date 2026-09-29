package fft

import chisel3._

class FFT extends Module {
  val io = IO(new Bundle {
    val input = Input(Vec(32, UInt(8.W)))
    val output = Output(Vec(32, UInt(8.W)))
  })

  // Placeholder logic
  io.output := io.input
}

object FFT extends App {
  emitVerilog(new FFT())
}
