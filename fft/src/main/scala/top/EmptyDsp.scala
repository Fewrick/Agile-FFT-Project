package top

import chisel3._

class EmptyDsp(override val samples : Int = 32) extends DspTop(samples) {
  val mem = SyncReadMem(samples, SInt(InputDataWidth.W))

  when (io.writeEn) {
    mem.write(io.writeAddr, io.writeData)
  }

  io.readData := mem.read(io.readAddr, io.readEn)

  io.busy := false.B
  io.done := true.B
}
