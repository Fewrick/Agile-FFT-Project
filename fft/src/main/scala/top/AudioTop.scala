package top

import chisel3._
import chisel3.util._

import audioIO.Microphone

class AudioTop(val samples: Int = 32) extends Module {
  require(samples >= 2 && isPow2(samples))

  val inputDataWidth = 8
  val addrWidth = log2Ceil(samples)

  val io = IO(new Bundle {
    val streamIn  = Input(Bool())
    val start     = Input(Bool())
    val mask      = Input(UInt(16.W))
    val streamOut = Output(UInt(inputDataWidth.W))
  })

  val mic = Module(new Microphone(inputDataWidth, samples))
  val dsp = Module(new FFTDsp(inputDataWidth, samples))

  mic.io.micEn := true.B
  mic.io.micDataStream := io.streamIn

  object State extends ChiselEnum {
    val Idle, Capture, CommitLastWrite, Launch, WaitForDsp, ReadOutput = Value
  }
  import State._

  val state = RegInit(Idle)
  val addr = RegInit(0.U(addrWidth.W))
  val lastAddr = (samples - 1).U(addrWidth.W)

  val startSync = RegNext(RegNext(io.start, false.B), false.B)
  val startRise = startSync && !RegNext(startSync, false.B)
  val acceptStart = state === Idle && startRise
  val writeSample = state === Capture && mic.io.writeEn

  // Microphone -> DSP register boundary.
  // Capture the sample, address, and write enable on the same edge.
  dsp.io.writeData := RegEnable(mic.io.writeData, writeSample)
  dsp.io.writeAddr := RegEnable(addr, writeSample)
  dsp.io.writeEn   := RegNext(writeSample, false.B)
  dsp.io.start     := state === Launch
  dsp.io.mask      := RegEnable(io.mask, acceptStart)

  dsp.io.readEn   := state === ReadOutput
  dsp.io.readAddr := addr

  // DSP -> output register boundary.
  io.streamOut := RegNext(dsp.io.readData.asUInt, 0.U(inputDataWidth.W))

  switch(state) {
    is(Idle) {
      when(acceptStart) {
        addr := 0.U
        state := Capture
      }
    }

    is(Capture) {
      when(writeSample) {
        when(addr === lastAddr) {
          state := CommitLastWrite
        }.otherwise {
          addr := addr + 1.U
        }
      }
    }

    is(CommitLastWrite) {
      state := Launch
    }

    is(Launch) {
      state := WaitForDsp
    }

    is(WaitForDsp) {
      when(dsp.io.done) {
        addr := 0.U
        state := ReadOutput
      }
    }

    is(ReadOutput) {
      when(addr === lastAddr) {
        state := Idle
      }.otherwise {
        addr := addr + 1.U
      }
    }
  }
}

object AudioTop extends App {
  val samples = sys.props.get("samples").map(_.toInt).getOrElse(32)
  emitVerilog(new AudioTop(samples))
}