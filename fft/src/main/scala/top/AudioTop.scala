package top

import chisel3._
import chisel3.util._

import audioIO.Microphone
import audioIO.PWMSpeaker

class AudioTop(val samples: Int = 32) extends Module {
  require(samples >= 2 && isPow2(samples))

  val inputDataWidth = 8
  val addrWidth = log2Ceil(samples)

  val io = IO(new Bundle {
    val streamIn  = Input(Bool())
    val start     = Input(Bool())
    val mask      = Input(UInt(16.W))
    val streamOut = Output(UInt(inputDataWidth.W))
    val micClkOut = Output(Bool())
    val audPwm    = Output(Bool())
    val audSd     = Output(Bool())
  })

  val mic = Module(new Microphone(inputDataWidth))
  val dsp = Module(new FFTDsp(inputDataWidth, samples))

  mic.io.micEn := true.B
  mic.io.micDataStream := io.streamIn
  io.micClkOut := mic.io.micClock

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

  // Speaker
  val speaker = Module(new NexysPWMSpeaker(inputDataWidth))
  val latchedSample = RegInit(0.U(inputDataWidth.W))

  // If dsp.io.readData is signed (-128 to 127), offset by +128 so silence/center is 128
  val unsignedSample = (dsp.io.readData.asSInt + 128.S).asUInt

  speaker.io.sampleIn := latchedSample
  io.audPwm := speaker.io.pwmOut
  io.audSd  := speaker.io.audioEn

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
      latchedSample := unsignedSample
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