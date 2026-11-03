package fft

import chisel3._
import chisel3.util.log2Ceil
import interfaces._

class FFTSeq(val inputWidth : Int, val FFTSize : Int) extends Module {
    val outputWidth = inputWidth + log2Ceil(FFTSize)
    val io = IO(new Bundle {
        val in = Flipped(new DSPIO(SInt(inputWidth.W)))
        val out = new DSPIO(new ComplexSample(outputWidth))
    })

    // Temporary placeholder
    io.in.ready := false.B
    io.out.data := 0.U.asTypeOf(io.out.data)
    io.out.valid := false.B
}