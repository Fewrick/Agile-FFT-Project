package buffer

import chisel3._
import chisel3.util._

class FrameLoader(val width: Int, val samples: Int, val recordingSamples: Int) extends Module {

  require(width > 0)
  require(samples >= 2 && isPow2(samples))
  require(recordingSamples >= samples)
  require(recordingSamples % samples == 0)

  private val addrWidth = log2Ceil(samples)
  private val recordingAddrWidth = log2Ceil(recordingSamples)

  val io = IO(new Bundle {
    val start = Input(Bool())
    val frameStart = Input(UInt(recordingAddrWidth.W))

    val busy = Output(Bool())
    val done = Output(Bool())

    // Recording memory interface
    val readEn   = Output(Bool())
    val readAddr = Output(UInt(recordingAddrWidth.W))
    val readData = Input(SInt(width.W))

    // DSP input memory interface
    val writeEn   = Output(Bool())
    val writeAddr = Output(UInt(addrWidth.W))
    val writeData = Output(SInt(width.W))
  })

  object State extends ChiselEnum {
    val Idle, Load, Drain, Done = Value
  }
  import State._

  val state = RegInit(Idle)
  val index = RegInit(0.U(addrWidth.W))
  val baseAddr = RegInit(0.U(recordingAddrWidth.W))

  // Address of the current sample in the recording memory:
  //
  // readAddr = frameStart + index
  //
  // The destination address is local to the FFT frame:
  //
  // writeAddr = index
  io.readEn   := state === Load
  io.readAddr := baseAddr + index

  // SyncReadMem has one cycle of read latency.
  // Delay the write address and enable to align with readData.
  io.writeEn   := RegNext(io.readEn, false.B)
  io.writeAddr := RegEnable(index, io.readEn)
  io.writeData := io.readData

  io.busy := state === Load || state === Drain
  io.done := state === Done

  switch(state) {
    is(Idle) {
      when(io.start) {
        assert(io.frameStart <= (recordingSamples - samples).U)
        baseAddr := io.frameStart
        index := 0.U
        state := Load
      }
    }

    is(Load) {
      when(index === (samples - 1).U) {
        state := Drain
      }.otherwise {
        index := index + 1.U
      }
    }

    // Allow the final synchronous memory response
    // to be written into the DSP input memory.
    is(Drain) {
      state := Done
    }

    is(Done) {
      state := Idle
    }
  }
}