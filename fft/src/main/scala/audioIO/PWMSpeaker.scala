package audioIO

import chisel3._
import chisel3.util._

import audioIO.Microphone

class PWMSpeaker(val width: Int = 8) extends Module {
val io = IO(new Bundle {
    val sampleIn = Input(UInt(width.W))
    val pwmOut   = Output(Bool())
    val audioEn  = Output(Bool())
  })

  // Free-running PWM counter
  val counter = RegInit(0.U(width.W))
  counter := counter + 1.U

  // When counter < sampleIn, output is active (0 in open-drain = pulled low;
  // when inactive, release line to Z so external pull-up / filter sets high)
  io.pwmOut  := !(counter < io.sampleIn)
  io.audioEn := true.B
}