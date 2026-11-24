package filter

import chisel3._
import chisel3.util._
import math.{log, pow}
import interfaces.ComplexSample

class BandpassFilter(val inputWidth : Int, val samples : Int, val bands : Int) extends Module {
    val io = IO(new Bundle {
        val mask = Input(UInt(bands.W))
        val freqIndex = Input(UInt(log2Ceil(samples).W))
        val in = Input(new ComplexSample(inputWidth))
        val out = Output(new ComplexSample(inputWidth))
    })

    val halfSize = samples / 2
    val d = log(halfSize)/log(2) / bands
    val cutoffs = (0 until bands).toList.map(i => pow(2.0, i*d))
    
    def find(bin: Int): Int = {
        for (i <- 0 until cutoffs.length - 1) {
            if (bin + 1 >= cutoffs(i) && bin + 1 < cutoffs(i + 1)) {
                return i
            }
        }
        return cutoffs.length - 1
    }

    val halfIdxList = (0 until halfSize).toList.map(bin => find(bin))
    // Due to the mirroring of the FFT output, input iindex 0 and input index samples-1 should 
    // map to the same switch, so should input index 1 and input index samples-2, etc.
    val idxList = List.concat(halfIdxList, halfIdxList.reverse)
    // Make one "lookup table" array for the scaled indices using the Scala "idxList" and converting from
    // Scala int to Chisel UInt, such that the input index is the array index and the value is the 
    // corresponding scaled index
    val scaledIndexArray = VecInit(idxList.map(i => i.U(log2Ceil(bands).W)))
    val scaledIndex = scaledIndexArray(io.freqIndex)

    io.out := Mux(io.mask(scaledIndex), io.in, 0.U.asTypeOf(new ComplexSample(inputWidth)))
}
