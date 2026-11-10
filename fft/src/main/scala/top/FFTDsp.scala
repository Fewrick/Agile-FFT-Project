
package top

import chisel3._
import chisel3.util._

import fft.{FFTSeq, IFFTSeq}
import interfaces.ComplexSample
import filter.BandpassFilter

class FFTDsp(override val inputDataWidth: Int, override val samples: Int) extends DspTop(inputDataWidth, samples, SInt(inputDataWidth.W)) {
    require(samples >= 2 && isPow2(samples))

    val fftWidth = inputDataWidth + log2Ceil(samples)

    // Processing modules
    val fft = Module(new FFTSeq(inputDataWidth, samples))
    val filter = Module(new BandpassFilter(fftWidth, samples))
    val ifft = Module(new IFFTSeq(inputDataWidth, samples))

    // Input and output memories
    val memIn = SyncReadMem(samples, SInt(inputDataWidth.W))
    val memOut = SyncReadMem(samples, SInt(inputDataWidth.W))

    // FSM
    object State extends ChiselEnum {
        val IDLE, READ, SEND, DRAIN, DONE = Value
    }
    import State._

    val state = RegInit(IDLE)

    // Counters
    val inputIndex = RegInit(0.U(inputAddrWidth.W))
    val filterIndex = RegInit(0.U(log2Ceil(samples).W))
    val outputIndex = RegInit(0.U(outputAddrWidth.W))

    val outputComplete = RegInit(false.B)
    val lastIndex = (samples - 1).U

    // Control signals
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
    // Input memory -> FFT
    // -------------------------

    fft.io.in.data := memIn.read(
        inputIndex,
        state === READ
    )

    fft.io.in.valid := state === SEND

    // -------------------------
    // FFT -> Filter
    // -------------------------

    filter.io.mask := io.mask
    filter.io.freqIndex := filterIndex
    filter.io.in := fft.io.out.data

    // -------------------------
    // Filter -> IFFT
    // -------------------------

    ifft.io.in.data := filter.io.out
    ifft.io.in.valid := fft.io.out.valid && busy

    // Backpressure from IFFT to FFT
    fft.io.out.ready := ifft.io.in.ready && busy

    // Advance filter index only when
    // a frequency sample is transferred
    when(fft.io.out.fire) {
        when(filterIndex === lastIndex) {
            filterIndex := 0.U
        }.otherwise {
            filterIndex := filterIndex + 1.U
        }
    }

    // -------------------------
    // IFFT -> Output memory
    // -------------------------

    ifft.io.out.ready := busy

    when(ifft.io.out.fire) {
        // Store only the real component
        memOut.write(
            outputIndex,
            ifft.io.out.data.re
        )

        when(outputIndex === lastIndex) {
            outputComplete := true.B
        }.otherwise {
            outputIndex := outputIndex + 1.U
        }
    }

    // -------------------------
    // Output memory read
    // -------------------------

    val readEn = io.readEn && state === DONE
    val readData = memOut.read(io.readAddr, readEn)
    val readValid = RegNext(readEn, false.B)

    io.readData := 0.S(inputDataWidth.W)

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
                filterIndex := 0.U
                outputIndex := 0.U
                outputComplete := false.B
                state := READ
            }
        }

        is(READ) {
            // Wait for synchronous RAM read
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
            // Wait for all IFFT results
            when(outputComplete) {
                state := DONE
            }
        }

        is(DONE) {
            when(io.start) {
                inputIndex := 0.U
                filterIndex := 0.U
                outputIndex := 0.U
                outputComplete := false.B
                state := READ
            }
        }
    }
}
