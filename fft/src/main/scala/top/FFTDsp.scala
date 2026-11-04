package top

import chisel3._
import chisel3.util._

import fft._
import interfaces.ComplexSample

class FFTDsp(override val inputDataWidth: Int, override val samples: Int) extends DspTop(inputDataWidth, samples, new ComplexSample(inputDataWidth + log2Ceil(samples))) {
    val fft = Module(new FFTSeq(inputDataWidth, samples))

    // Memories
    val memIn = SyncReadMem(samples, SInt(inputDataWidth.W))
    val memFFT = SyncReadMem(samples, new ComplexSample(outputDataWidth))

    // FSM
    object State extends ChiselEnum {
        val IDLE, READ, SEND, DRAIN, DONE = Value
    }
    import State._

    val state = RegInit(IDLE)

    // Counters
    val inputIndex = RegInit(0.U(inputAddrWidth.W))
    val outputIndex = RegInit(0.U(outputAddrWidth.W))

    val outputComplete = RegInit(false.B)

    val lastIndex = (samples - 1).U

    // Control outputs
    val busy = state =/= IDLE && state =/= DONE
    val done = state === DONE

    io.busy := busy
    io.done := done

    // -------------------------
    // Input memory write
    // -------------------------

    when(io.writeEn && !busy && !io.start) {
        memIn.write(io.writeAddr, io.writeData)
    }

    // -------------------------
    // Input memory read
    // -------------------------

    fft.io.in.data := memIn.read(inputIndex, state === READ)
    fft.io.in.valid := state === SEND

    // -------------------------
    // FFT output
    // -------------------------

    fft.io.out.ready := busy

    when(fft.io.out.fire) {
        memFFT.write(outputIndex, fft.io.out.data)
        when(outputIndex === lastIndex) {
            outputComplete := true.B
        }.otherwise {
            outputIndex := outputIndex + 1.U
        }
    } // IMPORTANT: Close the when block here.

    // -------------------------
    // Output memory read
    // -------------------------

    val readEn = io.readEn && state === DONE
    val readData = memFFT.read(io.readAddr, readEn)
    val readValid = RegNext(readEn, false.B)

    io.readData := 0.U.asTypeOf(new ComplexSample(outputDataWidth))

    when(readValid) {
        io.readData := readData
    }

    // -------------------------
    // State machine
    // -------------------------

    switch(state) {
        is(IDLE) {
            when(io.start) {
                inputIndex := 0.U
                outputIndex := 0.U
                outputComplete := false.B
                state := READ
            }
        }

        is(READ) {
            state := SEND
        }

        is(SEND) {
            when(fft.io.in.fire) {
                when(inputIndex === lastIndex) {
                    state := DRAIN
                }.otherwise {
                    inputIndex := inputIndex + 1.U
                    state := READ
                }
            }
        }

        is(DRAIN) {
            when(outputComplete) {
                state := DONE
            }
        }

        is(DONE) {
            when(io.start) {
                inputIndex := 0.U
                outputIndex := 0.U
                outputComplete := false.B
                state := READ
            }
        }
    }
}