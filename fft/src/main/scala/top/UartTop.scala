package top

import chisel3._
import chisel3.util._
import chisel.lib.uart._

class UartTop(val freq : Int, val baud : Int, val samples : Int = 32, val width : Int = 8) extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val tx = Output(Bool())
  })

  val tx = Module(new Tx(freq, baud))
  io.tx := tx.io.txd
  tx.io.channel.bits := DontCare
  tx.io.channel.valid := false.B

  val rx = Module(new Rx(freq, baud))
  rx.io.rxd := io.rx
  rx.io.channel.ready := false.B

  val dspTop = Module(new EmptyDsp(width, samples))
  dspTop.io.writeData := DontCare
  dspTop.io.writeAddr := DontCare
  dspTop.io.writeEn := false.B
  dspTop.io.start := false.B
  dspTop.io.readAddr := DontCare
  dspTop.io.readEn := DontCare

  object State extends ChiselEnum {
    val write, run, read = Value
  }

  val state = RegInit(State.write)
  val sample = RegInit(0.U(log2Up(samples).W))

  switch (state) {
    is (State.write) {
      rx.io.channel.ready := true.B
      when (rx.io.channel.valid) {
        dspTop.io.writeData := rx.io.channel.bits.asSInt
        dspTop.io.writeAddr := sample
        dspTop.io.writeEn := true.B
        sample := sample + 1.U
        when (sample === (samples - 1).U) {
          sample := 0.U
          state := State.run
        }
      }
    }

    is (State.run) {
      dspTop.io.start := true.B
      when (dspTop.io.done === true.B) {
        state := State.read
      }
    }

    is (State.read) {
      dspTop.io.readEn := true.B
      dspTop.io.readAddr := sample
      when (tx.io.channel.ready) {
        tx.io.channel.bits := dspTop.io.readData.asUInt
        tx.io.channel.valid := true.B
        sample := sample + 1.U
        when (sample === (samples - 1).U) {
          sample := 0.U
          state := State.write
        }
      }
    }
  }
}

object UartTop extends App {
  emitVerilog(new UartTop(100000000, 115200))
}
