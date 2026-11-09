package top

import chisel3._

class EmptyDsp(override val inputDataWidth : Int, override val samples : Int) extends DspTop(inputDataWidth, samples, SInt()) {
  val mem = SyncReadMem(samples, SInt(inputDataWidth.W))

  when (io.writeEn) {
    mem.write(io.writeAddr, io.writeData)
  }

  io.readData := mem.read(io.readAddr, io.readEn)

  io.busy := false.B
  io.done := true.B
}
