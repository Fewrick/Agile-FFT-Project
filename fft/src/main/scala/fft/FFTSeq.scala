package fft

import chisel3._
import chisel3.util._
import interfaces._

class FFTSeq(val inputWidth: Int, val samples: Int) extends Module {
    require(inputWidth > 0)
    require(samples >= 2 && isPow2(samples))

    val stages = log2Ceil(samples)
    val outputWidth = inputWidth + stages

    val io = IO(new Bundle {
        val in = Flipped(new DSPIO(SInt(inputWidth.W)))
        val out = new DSPIO(new ComplexSample(outputWidth))
    })

    val core = Module(new Radix2Seq(inputWidth, samples, inverse = false))
    core.io.in.data.re := io.in.data
    core.io.in.data.im := 0.S
    core.io.in.valid := io.in.valid
    io.in.ready := core.io.in.ready
    io.out <> core.io.out
}
