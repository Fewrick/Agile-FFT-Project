package filter

import chisel3._
import chisel3.util._
import interfaces.ComplexSample

// inputWidth = width of the data
class BandpassFilter(val inputWidth : Int, val samples : Int) extends Module {
    val io = IO(new Bundle {
        val mask = Input(UInt(16.W)) // 16 bit input from the on-board switches
        val freqIndex = Input(UInt(log2Ceil(samples).W))
        val in = Input(new ComplexSample(inputWidth))
        val out = Output(new ComplexSample(inputWidth))
    })
    val numSwitches = 16
    val halfSize = samples / 2
    val idxList = List.concat(
        // Due to the mirroring of the FFT output, input iindex 0 and input index samples-1 should 
        // map to the same switch, so should input index 1 and input index samples-2, etc.
        (0 until halfSize).toList.map(i => ((i * numSwitches) / halfSize)),
        (halfSize until samples).toList.map(i => (((samples - i - 1) * numSwitches) / halfSize))
    )
    // Make one "lookup table" array for the scaled indices using the Scala "idxList" and converting from
    // Scala int to Chisel UInt, such that the input index is the array index and the value is the 
    // corresponding scaled index
    val scaledIndexArray = VecInit(idxList.map(i => i.U(log2Ceil(numSwitches).W)))
    println("Scaled index array: ")
    val scaledIndex = scaledIndexArray(io.freqIndex)

    io.out := Mux(io.mask(scaledIndex), io.in, 0.U.asTypeOf(new ComplexSample(inputWidth)))

}