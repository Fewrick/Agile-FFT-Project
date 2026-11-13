package top

import chisel3._
import chisel3.util._
import audioio.{PdmDes, PCM}

class MicTop(val outputWidth: Int = 8, val samples: Int = 32) extends Module {
// class MicTop(val outputDataWidth: Int = 8) extends Module {
    require(outputWidth > 0)
    require((samples >= 2) & ((samples & (samples - 1)) == 0))
    val addrWidth = log2Ceil(samples)

    val io = IO(new Bundle {
        // Input microphone signals
        val micEn         = Input(Bool()) // Should come from a button (i think)
        val micDataStream = Input(Bool())

        // Output Memory
        val writeData = Output(SInt(outputWidth.W))
        val writeAddr = Output(UInt(addrWidth.W))
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
    io.writeData := pcm.io.pcmSample
    io.writeEn := pcm.io.pcmValid

// private val countWidth = log2Ceil(samples + 1)

// val io = IO(new Bundle {
//   val micEn         = Input(Bool())
//   val micDataStream = Input(Bool())
//   val pcmSamples    = Decoupled(SInt(outputWidth.W))
// })

// val pdm = Module(new PdmDes)
// val pcm = Module(new PCM(16, decimation, outputWidth))
// val fifo = Module(new Queue(SInt(outputWidth.W), entries = samples))

// val previousMicEn = RegNext(io.micEn, false.B)
// val startPressed = io.micEn && !previousMicEn

// object State extends ChiselEnum {
//   val Idle, Capture, Available = Value
// }
// import State._

// val state = RegInit(Idle)
// val captured = RegInit(0.U(countWidth.W))
// val readCount = RegInit(0.U(countWidth.W))

// pdm.io.clk          := clock
// pdm.io.en           := (state === Capture)
// pdm.io.pdm_m_data_i := io.micDataStream

// pcm.io.pdmWord  := pdm.io.dout
// pcm.io.pdmValid := pdm.io.done

// fifo.io.enq.valid := (state === Capture) && pcm.io.pcmValid
// fifo.io.enq.bits  := pcm.io.pcmSample

// io.pcmSamples.bits  := fifo.io.deq.bits
// io.pcmSamples.valid := (state === Available) && fifo.io.deq.valid
// fifo.io.deq.ready   := (state === Available) && io.pcmSamples.ready

// switch(state) {
//   is(Idle) {
//     when(startPressed) {
//       captured := 0.U
//       readCount := 0.U
//       state := Capture
//     }
//   }

//   is(Capture) {
//     when(pcm.io.pcmValid) {
//       when(captured === (samples - 1).U) {
//         state := Available
//       }.otherwise {
//         captured := captured + 1.U
//       }
//     }
//   }

//   is(Available) {
//     when(io.pcmSamples.fire) {
//       when(readCount === (samples - 1).U) {
//         readCount := 0.U
//         state := Idle
//       }.otherwise {
//         readCount := readCount + 1.U
//       }
//     }
//   }
// }
    
}
