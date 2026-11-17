package audioIO

import audioIO.{PCM, PdmDes}
import chisel3._
import chisel3.util._

// class Microphone(val outputWidth: Int = 8, val samples: Int = 32) extends Module {
class Microphone(val outputWidth: Int = 8) extends Module {
    require(outputWidth > 0)

    val io = IO(new Bundle {
        // Input microphone signals
        val micEn         = Input(Bool()) // Should come from a button (i think)
        val micDataStream = Input(Bool())

        // Output microphone signals
        val micClock = Output(Bool()) // Outputs a clock to the microphone's physical clock pin

        // Output Memory
        val writeData = Output(SInt(outputWidth.W))
        val writeEn   = Output(Bool())
        })
    
    // We want the signal processing (DSP) to happen for 8-bit inputs at 12kHz
    // In the Verilog PdmDec "blackbox", the PDM clock frequency (C_PDM_FREQ_HZ) 
    // is set to 2MHz and the output (dout) is 16 bit. 
    // An approximation of the decimation can be found as:
    // PCM sample rate = PDM clock rate / (PDM word width * decimation) 
    //                 = (PDM clock rate / PDM word width) / decimation
    // =>  12kHz = (2 MHz / 16) / decimation  =>  12kHz = 125 kHz / decimation  
    // =>  decimation = 125 kHz / 12 kHz = 10
    val decimation = 10

    // Processing modules
    val pdm = Module(new PdmDes())
    val pcm = Module(new PCM(16, decimation, outputWidth))

    // Input signals
    pdm.io.clk := clock 
    pdm.io.en := io.micEn 
    pdm.io.pdm_m_data_i := io.micDataStream

    // Signals connecting modules
    pcm.io.pdmWord := pdm.io.dout
    pcm.io.pdmValid := pdm.io.done
    
    // Output signals
    io.micClock := pdm.io.pdm_m_clk_o

    io.writeData := pcm.io.pcmSample
    io.writeEn := pcm.io.pcmValid
}
