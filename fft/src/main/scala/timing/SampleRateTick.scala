package timing

import chisel3._
import chisel3.util._

// Generate periodic sample ticks at a configurable sample rate.
class SampleRateTick(val sampleRate: Int, val clkFreq: Int) extends Module {
  require(sampleRate > 0 && clkFreq >= sampleRate)

  val io = IO(new Bundle {
    val enable = Input(Bool())
    val tick = Output(Bool())
  })

  // Number of clock cycles between consecutive sample ticks.
  private val period = math.max(1, math.round(clkFreq.toDouble / sampleRate.toDouble).toInt)
  private val cntWidth = math.max(1, log2Ceil(period))

  val cnt = RegInit(0.U(cntWidth.W))
  val wrap = cnt === (period - 1).U

  when(!io.enable || wrap) {
    cnt := 0.U
  }.otherwise {
    cnt := cnt + 1.U
  }

  // Generate a tick only when enabled and the period has elapsed.
  io.tick := io.enable && wrap
}