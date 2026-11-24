
package dsp

import chisel3._
import chisel3.util._
import fft.FFTSeq
import interfaces.ComplexSample

class FFTOnly(override val inputDataWidth: Int, override val samples: Int) extends DspTop(inputDataWidth, samples, new ComplexSample(inputDataWidth + log2Ceil(samples))) {
    require(samples >= 2 && isPow2(samples))

    val addrWidth = log2Ceil(samples)
    val lastIndex = (samples - 1).U(addrWidth.W)
    val fft = Module(new FFTSeq(inputDataWidth, samples))

    // Input and output memories
    val memIn  = SyncReadMem(samples, SInt(inputDataWidth.W))
    val memOut = SyncReadMem(samples, chiselTypeOf(fft.io.out.data))

    object State extends ChiselEnum {
        val Idle, Read, Send, Drain, Done = Value
    }; import State._

    val state = RegInit(Idle)
    val inputIndex  = RegInit(0.U(addrWidth.W))
    val outputIndex = RegInit(0.U(addrWidth.W))

    val busy = state =/= Idle && state =/= Done
    io.busy := busy
    io.done := state === Done

    when(io.writeEn && !busy && !io.start) {
        memIn.write(io.writeAddr, io.writeData)
    }

    // Keep reading the same sample if the FFT stalls in Send.
    fft.io.in.data := memIn.read(inputIndex, state === Read || state === Send)
    fft.io.in.valid := state === Send
    fft.io.out.ready := busy

    when(!busy && io.start) {
        inputIndex  := 0.U
        outputIndex := 0.U
        state := Read
    }.elsewhen(state === Read) {
        state := Send
    }.elsewhen(fft.io.in.fire) {
        when(inputIndex === lastIndex) {
            state := Drain
        }.otherwise {
            inputIndex := inputIndex + 1.U
            state := Read
        }
    }

    when(fft.io.out.fire) {
        memOut.write(outputIndex, fft.io.out.data)

        // This assignment takes priority if the final input also fires this cycle.
        when(outputIndex === lastIndex) {
            state := Done
        }.otherwise {
            outputIndex := outputIndex + 1.U
        }
    }

    val readEn = io.readEn && state === Done
    val readData = memOut.read(io.readAddr, readEn)
    val readValid = RegNext(readEn, false.B)

    io.readData := Mux(readValid, readData, 0.U.asTypeOf(readData))
}
