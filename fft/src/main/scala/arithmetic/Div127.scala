package arithmetic

import chisel3._
import chisel3.util._

class Div127(val width: Int = 16) extends Module {
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
  val correction = PopCount(Seq(residue >= 127.U, residue >= 254.U, residue >= 381.U))

  val quotient = Wire(UInt(width.W))
  quotient := coarseQuotient +& correction

  io.out := Mux(negative, 0.U(width.W) - quotient, quotient).asSInt
}