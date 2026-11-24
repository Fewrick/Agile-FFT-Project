package buffer

import chisel3._
import chisel3.util._

class FrameStore(val width: Int, val samples: Int, val recordingSamples: Int) extends Module {

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

    // DSP output memory interface
    val readEn   = Output(Bool())
    val readAddr = Output(UInt(addrWidth.W))
    val readData = Input(SInt(width.W))

    // Recording memory interface
    val writeEn   = Output(Bool())
    val writeAddr = Output(UInt(recordingAddrWidth.W))
    val writeData = Output(SInt(width.W))
  })

  object State extends ChiselEnum {
    val Idle, Store, Drain, Done = Value
  }
  import State._

  val state = RegInit(Idle)
  val index = RegInit(0.U(addrWidth.W))
  val baseAddr = RegInit(0.U(recordingAddrWidth.W))

  // Read from the local DSP output memory.
  // readAddr = index
  io.readEn   := state === Store
  io.readAddr := index

  // Write to the corresponding global recording address.
  // writeAddr = frameStart + index
  //
  // Delay the write address and enable by one cycle
  // to account for the synchronous DSP memory read.
  io.writeEn   := RegNext(io.readEn, false.B)
  io.writeAddr := RegEnable(baseAddr + index, io.readEn)
  io.writeData := io.readData

  io.busy := state === Store || state === Drain
  io.done := state === Done

  switch(state) {
    is(Idle) {
      when(io.start) {
        assert(io.frameStart <= (recordingSamples - samples).U)
        baseAddr := io.frameStart
        index := 0.U
        state := Store
      }
    }

    is(Store) {
      when(index === (samples - 1).U) {
        state := Drain
      }.otherwise {
        index := index + 1.U
      }
    }

    // Commit the final synchronous memory response
    // before asserting done.
    is(Drain) {
      state := Done
    }

    is(Done) {
      state := Idle
    }
  }
}