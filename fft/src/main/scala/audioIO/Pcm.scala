package audioIO

import chisel3._
import chisel3.util._

class PCM(
    // Width of each PDM word
    pdmWordWidth: Int = 16,
    // Number of PDM words to accumulate before producing a PCM sample
    decimation: Int = 10,
    outputWidth: Int = 8
) extends Module {
    require(pdmWordWidth > 0)
    require(decimation > 0)
    require(outputWidth == 8)

    // Calculating the total number of bits in the PDM stream and the width of the counters
    // bits accumulated per PCM sample: 16x10 = 160 bits
    val totalBits = pdmWordWidth * decimation
    val midpoint = totalBits / 2
    val countWidth = log2Ceil(totalBits + 1)
    val wordCountWidth = math.max(1, log2Ceil(decimation))

    val io = IO(new Bundle {
        val pdmWord = Input(UInt(pdmWordWidth.W))
        val pdmValid = Input(Bool())

        val pcmSample = Output(SInt(outputWidth.W))
        val pcmValid = Output(Bool())
    })

    // Setting up registers for counting the number of words and ones in the PDM stream
    val wordCount = RegInit(0.U(wordCountWidth.W))
    val oneCount = RegInit(0.U(countWidth.W))

    val currentOnes = PopCount(io.pdmWord)
    val nextOneCount = oneCount + currentOnes
    val lastWord = wordCount === (decimation - 1).U
    val finishWindow = io.pdmValid && lastWord

    // Capture the count including the final word before clearing the accumulator.
    val completedCount = RegEnable(nextOneCount, finishWindow)
    val completedValid = RegNext(finishWindow, false.B)

    when(io.pdmValid) {
        when(lastWord) {
            wordCount := 0.U
            oneCount := 0.U
        }.otherwise {
            wordCount := wordCount + 1.U
            oneCount := nextOneCount
        }
    }

    // calculate the PCM sample, subtracting half the totalt number of bits to center the value around zero
    // 50% ones in the PDM stream will in the PCM sample be 0
    // higher number of ones -> positive value
    // fewer number of ones -> negative value
    // scaling the value then makes it fit into the output width of 8 bits
    val scaledSample = if (totalBits == 160) {
        val pcmTable = VecInit((0 to totalBits).map { ones =>
            val sample = ((ones - midpoint) * 127) / midpoint
            sample.S(outputWidth.W)
        })
        pcmTable(completedCount)
    } else {
        val centered = completedCount.asSInt - midpoint.S
        val scaled = (centered * 127.S) / midpoint.S
        scaled(outputWidth - 1, 0).asSInt
    }

    val pcmSample = RegInit(0.S(outputWidth.W))
    when(completedValid) {
        pcmSample := scaledSample
    }

    io.pcmSample := pcmSample
    io.pcmValid := RegNext(completedValid, false.B)
}