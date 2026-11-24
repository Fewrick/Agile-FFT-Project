package arithmetic

import chisel3._
import chisel3.util._

class LinearInterpolation(val dataWidth: Int, val samples: Int, val overlapSamples: Int) extends Module {
    require(isPow2(samples))
    require(isPow2(overlapSamples))
    require(overlapSamples >= 2)
    require(overlapSamples <= samples / 2)

    private val shift = log2Ceil(overlapSamples)

    val io = IO(new Bundle {
        val a = Input(SInt(dataWidth.W))
        val b = Input(SInt(dataWidth.W))

        val index = Input(UInt(shift.W))

        val out = Output(SInt(dataWidth.W))
    })

    val diff = io.b -& io.a

    val terms = (0 until shift).map { k =>
        Mux(io.index(k), diff << k, 0.S)
    }

    val weighted = terms.reduceTree(_ +& _)

    io.out := io.a +& (weighted >> shift)
}