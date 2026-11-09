package top

import chisel3._
import chisel3.util._

abstract class DspTop[T <: Data](val inputDataWidth : Int, val samples : Int, outputGen: T) extends Module {
  require(inputDataWidth > 0)
  require((samples >= 2) & ((samples & (samples - 1)) == 0))

  val outputDataWidth = inputDataWidth + log2Ceil(samples)
  val inputAddrWidth = log2Ceil(samples)
  val outputAddrWidth = log2Ceil(samples)

  val io = IO(new Bundle {
    // Input Memory
    val writeData = Input(SInt(inputDataWidth.W))
    val writeAddr = Input(UInt(inputAddrWidth.W))
    val writeEn = Input(Bool())

    // Processing control
    val start = Input(Bool())
    val busy = Output(Bool())
    val done = Output(Bool())

    // Output Memory
    val readData = Output(outputGen)
    val readAddr = Input(UInt(outputAddrWidth.W))
    val readEn = Input(Bool())
  })
}