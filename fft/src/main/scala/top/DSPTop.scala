package top

import chisel3._
import chisel3.util.log2Ceil

class DSPTop() extends Module {
    val io = IO(new Bundle {
        val writeData = Input(SInt(InputDataWidth.W))
        val writeAddr = Input(UInt(InputAddrWidth.W))
        val wr_en = Input(Bool())
        val start = Input(Bool())

        val readData = Output(SInt(OutputDataWidth.W))
        val readAddr = Input(UInt(OutputAddrWidth.W))
        val done = Output(Bool())
    })


    val fft = Module(new FFT(32))


}