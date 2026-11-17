package audioIO

import chisel3._
import chisel3.util._

class Microphone(val outputWidth: Int = 8, val samples: Int = 32) extends Module {
  require(outputWidth > 0)

  val io = IO(new Bundle {
    val micEn         = Input(Bool())
    val micDataStream = Input(Bool())

    val writeData = Output(SInt(outputWidth.W))
    val writeEn   = Output(Bool())
  })

  val decimation = 10
  val pdm = Module(new PdmDes())
  val pcm = Module(new PCM(16, decimation, outputWidth))

  pdm.io.clk := clock
  pdm.io.en := io.micEn
  pdm.io.pdm_m_data_i := io.micDataStream

  // PdmDes -> PCM register boundary. Capture the word and valid together.
  val pdmWordReg = RegEnable(pdm.io.dout, pdm.io.done)
  val pdmValidReg = RegNext(pdm.io.done, false.B)

  pcm.io.pdmWord := pdmWordReg
  pcm.io.pdmValid := pdmValidReg

  io.writeData := pcm.io.pcmSample
  io.writeEn := pcm.io.pcmValid
}