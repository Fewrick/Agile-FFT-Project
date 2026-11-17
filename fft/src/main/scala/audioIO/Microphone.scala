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

  // We want the signal processing (DSP) to happen for 8-bit inputs at 12kHz.
  // In the Verilog PdmDec "blackbox", the PDM clock frequency assumed to be 2MHz and the output (dout) is 16 bit. 
  // An approximation of the decimation can be found as:
  // PCM sample rate = PDM clock rate / (PDM word width * decimation) 
  //                 = (PDM clock rate / PDM word width) / decimation
  // =>  12kHz = (2 MHz / 16) / decimation = 125 kHz / decimation  
  // =>  decimation = 125 kHz / 12 kHz = 10
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
