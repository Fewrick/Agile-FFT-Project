package timing

import chisel3._
import chisel3.util._

// Generate periodic sample ticks at a configurable sample rate.
class SampleRateTick(val sampleRate: Int, val clkFreq: Int) extends Module {
  require(sampleRate > 0 && clkFreq >= sampleRate)

  require(clkFreq % sampleRate == 0, "Sample rate must divide the clock frequency exactly")

  val io = IO(new Bundle {
    val enable = Input(Bool())
    val tick = Output(Bool())
  })

  // Number of clock cycles between consecutive sample ticks.
  private val period = clkFreq / sampleRate
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