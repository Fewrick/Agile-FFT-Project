package top

import chisel3._
import chisel3.util._

class AudioCapture(val captureSamples: Int = 12000, val capacity: Int = 16384, val width: Int = 8) extends Module {
  require(width > 0)
  require(capacity >= 2 && isPow2(capacity))
  require(captureSamples > 0 && captureSamples <= capacity)

  private val addrWidth = log2Ceil(capacity)

  val io = IO(new Bundle {
    val start = Input(Bool())
    val sample = Input(SInt(width.W))
    val sampleValid = Input(Bool())
    val busy = Output(Bool())
    val done = Output(Bool())
    val readAddr = Input(UInt(addrWidth.W))
    val readEn = Input(Bool())
    val readData = Output(SInt(width.W))
  })

  val memory = SyncReadMem(capacity, SInt(width.W))
  val addr = RegInit(0.U(addrWidth.W))
  val busy = RegInit(false.B)
  val done = RegInit(false.B)

  io.busy := busy
  io.done := done
  io.readData := memory.read(io.readAddr, io.readEn)

  when(!busy && io.start) {
    addr := 0.U
    busy := true.B
    done := false.B
  }.elsewhen(busy && io.sampleValid) {
    memory.write(addr, io.sample)
    when(addr === (captureSamples - 1).U) {
      busy := false.B
      done := true.B
    }.otherwise {
      addr := addr + 1.U
    }
  }
}
