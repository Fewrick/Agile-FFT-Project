package fft

import chisel3._
import chisel3.util._
import interfaces._

class IFFTSeq(val outputWidth: Int, val samples: Int) extends Module {
    require(outputWidth > 0)
    require(samples >= 2 && isPow2(samples))

    val stages = log2Ceil(samples)
    val inputWidth = outputWidth + stages

    val io = IO(new Bundle {
        val in = Flipped(new DSPIO(new ComplexSample(inputWidth)))
        val out = new DSPIO(new ComplexSample(outputWidth))
    })

    val core = Module(new Radix2Seq(outputWidth, samples, inverse = true))
    core.io.in <> io.in
    io.out.data.re := core.io.out.data.re(outputWidth - 1, 0).asSInt
    io.out.data.im := core.io.out.data.im(outputWidth - 1, 0).asSInt
    io.out.valid := core.io.out.valid
    core.io.out.ready := io.out.ready
}
