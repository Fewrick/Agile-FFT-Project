package audioIO

import chisel3._
import chisel3.util._

class Microphone(val outputWidth: Int = 8) extends Module {
  require(outputWidth > 0)

  val io = IO(new Bundle {
    // Input microphone signals
    val micEn         = Input(Bool())
    val micDataStream = Input(Bool())

    // Output microphone signal
    val micClock = Output(Bool())

    val writeData = Output(SInt(outputWidth.W))
    val writeEn   = Output(Bool())
  })

  // PdmDes toggles every 25 system clocks: 100 MHz / 50 = 2 MHz PDM.
  // Each PCM output accumulates 10 words of 16 bits: 12,500 Hz exactly.
  // The divider is fixed; AudioTop enforces the matching clock/sample rate.
  val decimation = 10
  val pdm = Module(new PdmDes())
  val pcm = Module(new PCM(16, decimation, outputWidth))

  // Input signals
  pdm.io.clk := clock
  pdm.io.en := io.micEn
  pdm.io.pdm_m_data_i := io.micDataStream

  // PdmDes -> PCM register boundary. Capture the word and valid together.
  val pdmWordReg = RegEnable(pdm.io.dout, pdm.io.done)
  val pdmValidReg = RegNext(pdm.io.done, false.B)

  pcm.io.pdmWord := pdmWordReg
  pcm.io.pdmValid := pdmValidReg
    
  // Output signals
  io.micClock := pdm.io.pdm_m_clk_o

  io.writeData := pcm.io.pcmSample
  io.writeEn := pcm.io.pcmValid
}
