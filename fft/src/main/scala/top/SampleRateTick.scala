package top

import chisel3._
import chisel3.util._

// Nexys A7 100T has a 100 MHz clock
class SampleRateTick(val sampleRate: Int) extends Module {
  require(sampleRate > 0 && 100000000 >= sampleRate)

  val clkFreq = 100000000

  val io = IO(new Bundle {
    val tick = Output(Bool())
  })

  private val period = math.max(1, math.round(clkFreq.toDouble / sampleRate.toDouble).toInt)
  private val cntWidth = math.max(1, log2Ceil(period))

  val cnt = RegInit(0.U(cntWidth.W))
  val wrap = cnt === (period - 1).U

  when(wrap) {
    cnt := 0.U
  }.otherwise {
    cnt := cnt + 1.U
  }

  io.tick := wrap
}