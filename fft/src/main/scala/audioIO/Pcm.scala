package audioio

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

    // Bits accumulated per PCM sample: pdmWordWidth * decimation.
    val totalBits = pdmWordWidth * decimation
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

    val pcmSample = RegInit(0.S(outputWidth.W))
    val pcmValid = RegInit(false.B)

    val currentOnes = PopCount(io.pdmWord)
    val nextOneCount = oneCount + currentOnes

    io.pcmSample := pcmSample
    io.pcmValid := pcmValid

    pcmValid := false.B

    when(io.pdmValid) {
        when(wordCount === (decimation - 1).U) {
            // Center 50% density at zero and scale to signed 8-bit range.
            val centered = nextOneCount.asSInt - (totalBits / 2).S
            val scaled = (centered * 127.S) / (totalBits / 2).S

            pcmSample := scaled(outputWidth - 1, 0).asSInt
            pcmValid := true.B

            wordCount := 0.U
            oneCount := 0.U
        }.otherwise {
            wordCount := wordCount + 1.U
            oneCount := nextOneCount
        }
    }
}