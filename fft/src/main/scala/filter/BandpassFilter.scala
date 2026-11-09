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

    // The n input indices needs to be scaled/mapped to 16 to correspond to the mask from the switches
    // For now, this is done linearly, but ideally the scaling should be done logarithmically

    // Compile-time scala function to map all possible indices to the corresponding 16 switches
    // def scaleIndices(samples: Int): List[Int] = {
    //     val halfSize = samples / 2
    //     val posIdxList = (0 until halfSize - 1).toList
    //     val negIdxList = (halfSize until samples - 1).toList
    //     List.concat(
    //         // Due to the mirroring of the FFT output, input iindex 0 and input index samples-1 should 
    //         // map to the same switch, so should input index 1 and input index samples-2, etc.
    //         posIdxList.map(i => ((i * 16) / halfSize).floor.toInt),
    //         negIdxList.map(i => (((samples - i - 1) * 16) / halfSize).floor.toInt)
    //     )
    // }
    // val idxList = scaleIndices(samples)
    
    val halfSize = samples / 2
    val idxList = List.concat(
        // Due to the mirroring of the FFT output, input iindex 0 and input index samples-1 should 
        // map to the same switch, so should input index 1 and input index samples-2, etc.
        (0 until halfSize).toList.map(i => ((i * 16) / halfSize)),
        (halfSize until samples).toList.map(i => (((samples - i - 1) * 16) / halfSize))
    )
    // Make one "lookup table" array for the scaled indices using the Scala "idxList" and converting from
    // Scala int to Chisel UInt, such that the input index is the array index and the value is the 
    // corresponding scaled index
    val scaledIndexArray = VecInit(idxList.map(i => i.U(log2Ceil(16).W)))
    println("Scaled index array: ")
    val scaledIndex = scaledIndexArray(io.freqIndex)

    io.out := Mux(io.mask(scaledIndex), io.in, 0.U.asTypeOf(new ComplexSample(inputWidth)))

}