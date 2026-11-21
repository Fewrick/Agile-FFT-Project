package util

import chisel3._
import chisel3.util.log2Ceil
import fft.{FFTSeq, IFFTSeq}
import interfaces.{ComplexSample, DSPIO}

// Exercise the public wrappers through one full-width complex test interface.
class SeqTransformHarness(width: Int, samples: Int, inverse: Boolean) extends Module {
  val fullWidth = width + log2Ceil(samples)
  val io = IO(new Bundle {
    val in = Flipped(new DSPIO(new ComplexSample(fullWidth)))
    val out = new DSPIO(new ComplexSample(fullWidth))
  })
  if (inverse) {
    val engine = Module(new IFFTSeq(width, samples))
    engine.io.in <> io.in
    io.out.data.re := engine.io.out.data.re
    io.out.data.im := engine.io.out.data.im
    io.out.valid := engine.io.out.valid
    engine.io.out.ready := io.out.ready
  } else {
    val engine = Module(new FFTSeq(width, samples))
    engine.io.in.data := io.in.data.re(width - 1, 0).asSInt
    engine.io.in.valid := io.in.valid
    io.in.ready := engine.io.in.ready
    io.out <> engine.io.out
  }
}

