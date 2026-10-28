package fft

import chisel3._
import interfaces._

class FFTSeq extends Module {
    val io = IO(new Bundle {
        val in = Flipped(new DSPIO(SInt(8.W)))
        val out = new DSPIO(new ComplexSample(13))
    })

    // Temporary placeholder
    io.in.ready := false.B
    io.out.data := 0.U.asTypeOf(io.out.data)
    io.out.valid := false.B
}