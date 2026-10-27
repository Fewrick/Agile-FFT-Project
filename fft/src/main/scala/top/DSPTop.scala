package top

import chisel3._
import chisel3.util.log2Ceil

class DSPTop(val FFTSize : Int = 32) extends Module {
    val InputDataWidth = 8
    val OutputDataWidth = InputDataWidth + log2Ceil(FFTSize)
    val InputAddrWidth = log2Ceil(FFTSize)
    val OutputAddrWidth = log2Ceil(FFTSize)

    val io = IO(new Bundle {
        // Input Memory
        val writeData = Input(SInt(InputDataWidth.W))
        val writeAddr = Input(UInt(InputAddrWidth.W))
        val writeEn = Input(Bool())

        // Processing control
        val start = Input(Bool())
        val busy = Output(Bool())
        val done = Output(Bool())

        // Output Memory
        val readData = Output(SInt(OutputDataWidth.W))
        val readAddr = Input(UInt(OutputAddrWidth.W))
        val readEn = Input(Bool())
        
    })


    val fft = Module(new FFT(32))


}