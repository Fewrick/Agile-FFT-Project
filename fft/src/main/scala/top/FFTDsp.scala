package top

import chisel3._
import chisel3.util._

import fft._
import interfaces.ComplexSample

class FFTDsp(override val inputDataWidth : Int, override val samples : Int) extends DspTop(inputDataWidth, samples) {

    val fft = Module(new FFTSeq(inputDataWidth, samples))

    // Memories
    val memIn = SyncReadMem(samples, SInt(inputDataWidth.W))
    val memFFT = SyncReadMem(samples, new ComplexSample(outputDataWidth))

    // FSM
    object State extends ChiselEnum {
        val IDLE, LOAD, FFT, DONE = Value
    }; import State._
    val state = RegInit(IDLE)

    // Write into input memory
    when (io.writeEn & !io.busy) {
        memIn.write(io.writeAddr, io.writeData)
    }

    when (io.start) {
        io.busy := 1.B
        io.done := 0.B
        // Start FFT chain
    }

    when(fft.io.out.valid) {
        memFFT.write(0.U, fft.io.out.data)
    }








}