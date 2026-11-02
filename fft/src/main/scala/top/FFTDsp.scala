package top

import chisel3._

import fft._

class FFTDsp(val FFTSize : Int = 32) extends DspTop(FFTSize) {
    val memA = SyncReadMem(FFTSize, SInt(InputDataWidth.W))

    val fft = Module(new FFTSeq()) // TODO: replace with sequential FFT module

    val memB = SyncReadMem(FFTSize, SInt(OutputDataWidth.W))


}