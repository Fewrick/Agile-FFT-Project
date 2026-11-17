package arithmetic

import chisel3._
import chisel3.util._

class Div127(val width: Int = 16, val pipelined: Boolean = false) extends Module {
  require(width >= 1 && width <= 27)

  val io = IO(new Bundle {
    val in  = Input(SInt(width.W))
    val out = Output(SInt(width.W))
  })

  val negative = io.in(width - 1)
  val bits = io.in.asUInt

  // An unsigned magnitude can represent the most-negative signed input.
  val magnitude = Mux(negative, 0.U(width.W) - bits, bits).pad(22)

  val high1 = magnitude >> 7
  val high2 = magnitude >> 14
  val high3 = magnitude >> 21

  // Since 128 = 127 + 1, magnitude = 127 * coarseQuotient + residue.
  val coarseQuotient = (high1 +& high2) +& high3
  val lowDigits = magnitude(6, 0) +& high1(6, 0)
  val highDigits = high2(6, 0) +& high3
  val residue = lowDigits +& highDigits

  // For the supported widths, residue is less than 4 * 127.
  val (coarse, remainder, negate) =
    if (pipelined) {
      (RegNext(coarseQuotient), RegNext(residue), RegNext(negative))
    } else {
      (coarseQuotient, residue, negative)
    }

  val correction = PopCount(Seq(
    remainder >= 127.U,
    remainder >= 254.U,
    remainder >= 381.U
  ))

  val quotient = Wire(UInt(width.W))
  quotient := coarse +& correction

  io.out := Mux(negate, 0.U(width.W) - quotient, quotient).asSInt
}