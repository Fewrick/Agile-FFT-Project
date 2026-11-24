package audioIO

import chisel3._
import chisel3.util._
import timing.SampleRateTick

class Speaker( val width: Int, val recordingSamples: Int, val sampleRate: Int, val clkFreq: Int) extends Module {

  require(width > 0)
  require(recordingSamples > 0)
  require(sampleRate > 0 && sampleRate <= clkFreq)

  require(clkFreq / sampleRate >= 2, "Speaker requires at least two clocks per sample for synchronous RAM prefetch")

  private val addrWidth = math.max(1, log2Ceil(recordingSamples))

  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())
    val busy  = Output(Bool())

    // Recording memory interface
    val readEn   = Output(Bool())
    val readAddr = Output(UInt(addrWidth.W))
    val readData = Input(SInt(width.W))

    // Speaker interface
    val sampleOut = Output(UInt(width.W))
    val audPwm    = Output(Bool())
    val audSd     = Output(Bool())
  })

  val pwm = Module(new PWMSpeaker(width))
  val tick = Module(new SampleRateTick(sampleRate, clkFreq))

  object State extends ChiselEnum {
    val Idle, ReadFirst, WaitFirst, Playback, Done = Value
  }
  import State._

  val state = RegInit(Idle)

  val addr = RegInit(0.U(addrWidth.W))
  val lastAddr = (recordingSamples - 1).U(addrWidth.W)

  // Signed PCM silence corresponds to the unsigned midpoint.
  val midpoint = (BigInt(1) << (width - 1)).U(width.W)
  val sample = RegInit(midpoint)

  // Convert signed PCM to unsigned by flipping the sign bit.
  val unsignedSample = io.readData.asUInt ^ midpoint

  // Prefetch the next sample while the current sample is playing. The
  // synchronous RAM response never shifts the sample-period boundary.
  val nextSample = RegInit(midpoint)
  val readsComplete = RegInit(false.B)
  val playingIndex = RegInit(0.U(addrWidth.W))
  val lastSample = playingIndex === lastAddr
  val readValid = RegNext(io.readEn, false.B)

  tick.io.enable := state === Playback
  io.readEn := state === ReadFirst ||
    (state === WaitFirst && readValid && !readsComplete) ||
    (state === Playback && tick.io.tick && !readsComplete)
  io.readAddr := addr

  when(io.readEn) {
    assert(addr < recordingSamples.U)
    when(addr === lastAddr) {
      readsComplete := true.B
    }.otherwise {
      addr := addr + 1.U
    }
  }
  when(readValid && state === Playback) {
    nextSample := unsignedSample
  }

  pwm.io.sampleIn := sample
  io.sampleOut := sample
  io.audPwm := pwm.io.pwmOut
  io.audSd := pwm.io.audioEn
  io.busy := state =/= Idle && state =/= Done
  io.done := state === Done

  switch(state) {
    is(Idle) {
      when(io.start) {
        addr := 0.U
        playingIndex := 0.U
        readsComplete := false.B
        state := ReadFirst
      }
    }
    is(ReadFirst) { state := WaitFirst }
    is(WaitFirst) {
      when(readValid) {
        sample := unsignedSample
        state := Playback
      }
    }
    is(Playback) {
      when(tick.io.tick) {
        when(lastSample) {
          sample := midpoint
          state := Done
        }.otherwise {
          sample := nextSample
          playingIndex := playingIndex + 1.U
        }
      }
    }
    is(Done) { state := Idle }
  }
}
