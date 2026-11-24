package top

import chisel3._
import chisel3.util._
import chisel3.experimental.{annotate, ChiselAnnotation}

import audioIO.{Microphone, Speaker}
import buffer.{FrameLoader, FrameStore}
import dsp.FFTDsp

class AudioTop(val samples: Int, val width: Int, val sampleRate: Int, val recordingSamples: Int, val clkFreq: Int = 100000000, val bands: Int) extends Module {
  require(samples >= 2 && isPow2(samples))
  require(width == 8, "Microphone PCM currently supports eight-bit samples only")
  require(clkFreq == 100000000 && sampleRate == 12500,
    "Fixed microphone divider supports only 100 MHz / 12,500 Hz")
  require(sampleRate > 0 && sampleRate <= clkFreq)
  require(recordingSamples >= samples)
  require(recordingSamples % samples == 0, "recordingSamples must be divisible by samples")

  val frames = recordingSamples / samples

  val recordingAddrWidth = math.max(1, log2Ceil(recordingSamples))

  val io = IO(new Bundle {
    val streamIn  = Input(Bool())
    val start     = Input(Bool()) // Capture a new recording
    val process   = Input(Bool()) // FFT -> mask -> IFFT, in place
    val output    = Input(Bool()) // Play the current recording
    val mask      = Input(UInt(bands.W))
    val streamOut = Output(UInt(width.W))
    val micClkOut = Output(Bool())
    val audPwm    = Output(Bool())
    val audSd     = Output(Bool())
  })

  // Recording memory for captured samples
  val recordingMem = SyncReadMem(recordingSamples, SInt(width.W), SyncReadMem.ReadFirst)
  annotate(new ChiselAnnotation {
    override def toFirrtl = firrtl.AttributeAnnotation(recordingMem.toTarget, "ram_style = \"block\"")
  })
  val recordingAddr = RegInit(0.U(recordingAddrWidth.W))
  val lastRecordingAddr = (recordingSamples - 1).U

  // Module instantiations
  val mic = Module(new Microphone(width))
  val dsp = Module(new FFTDsp(width, samples, bands))
  val loader = Module(new FrameLoader(width, samples, recordingSamples))
  val store = Module(new FrameStore(width, samples, recordingSamples))
  val speaker = Module(new Speaker(width, recordingSamples, sampleRate, clkFreq))

  // FrameLoader -> DSP input memory
  dsp.io.writeEn   := loader.io.writeEn
  dsp.io.writeAddr := loader.io.writeAddr
  dsp.io.writeData := loader.io.writeData

  // State machine
  object State extends ChiselEnum {
    val Idle, Capture, RecordingDone, LoadFrame, Launch, WaitForDsp, StoreFrame, ProcessingDone, Playback = Value
  }
  import State._

  val state = RegInit(Idle)

  // Starting address of the current FFT frame in recordingMem
  val frameStart = RegInit(0.U(recordingAddrWidth.W))
  val lastFrameStart = (recordingSamples - samples).U(recordingAddrWidth.W)

  // Microphone
  mic.io.micEn := true.B
  mic.io.micDataStream := io.streamIn
  io.micClkOut := mic.io.micClock

  // FrameLoader
  loader.io.start := state === LoadFrame
  loader.io.frameStart := frameStart

  // FrameStore
  store.io.start := state === StoreFrame
  store.io.frameStart := frameStart

  // DSP output memory -> FrameStore
  dsp.io.readEn   := store.io.readEn
  dsp.io.readAddr := store.io.readAddr
  store.io.readData := dsp.io.readData

  // Recording memory -> FrameLoader
  // Share the recording memory read port between
  // frame loading and audio playback.
  val memReadEn = loader.io.readEn || speaker.io.readEn
  val memReadAddr = Mux(speaker.io.readEn, speaker.io.readAddr, loader.io.readAddr)
  val memReadData = recordingMem.read(memReadAddr, memReadEn)

  loader.io.readData  := memReadData
  speaker.io.readData := memReadData

  // Frame loading and playback must never occur simultaneously.
  assert(!(loader.io.readEn && speaker.io.readEn))

  // Synchronize each physical button and accept only a fresh rising edge.
  def buttonRise(button: Bool): Bool = {
    val synced = RegNext(RegNext(button, false.B), false.B)
    synced && !RegNext(synced, false.B)
  }
  val startRise = buttonRise(io.start)
  val processRise = buttonRise(io.process)
  val outputRise = buttonRise(io.output)
  val hasRecording = RegInit(false.B)
  // Simultaneous presses have deterministic priority: capture, process, output.
  // Busy presses are ignored rather than queued for a later operation.
  val acceptStart = state === Idle && startRise
  val acceptProcess = state === Idle && hasRecording && !startRise && processRise
  val acceptOutput = state === Idle && hasRecording && !startRise && !processRise && outputRise
  val writeSample = state === Capture && mic.io.writeEn

  // Share the recording memory write port between
  // microphone acquisition and processed frame storage.
  val memWriteEn = writeSample || store.io.writeEn

  val memWriteAddr = Mux(
    store.io.writeEn,
    store.io.writeAddr,
    recordingAddr
  )

  val memWriteData = Mux(
    store.io.writeEn,
    store.io.writeData,
    mic.io.writeData
  )

  when(memWriteEn) {
    recordingMem.write(memWriteAddr, memWriteData)
  }

  // Acquisition and frame storage must never occur simultaneously.
  assert(!(writeSample && store.io.writeEn))

  assert(!(memReadEn && memWriteEn), "Recording RAM access phases must be exclusive")
  when(memReadEn) { assert(memReadAddr < recordingSamples.U) }
  when(memWriteEn) { assert(memWriteAddr < recordingSamples.U) }
  when(loader.io.writeEn) { assert(!dsp.io.busy && !dsp.io.start) }
  when(dsp.io.start) { assert(!dsp.io.busy && !loader.io.busy) }
  when(store.io.readEn) { assert(dsp.io.done) }
  when(loader.io.done) { assert(!loader.io.writeEn && !loader.io.busy) }
  when(store.io.done) { assert(!store.io.writeEn && !store.io.busy) }
  when(state =/= Idle && state =/= Capture) {
    assert(frameStart <= lastFrameStart)
  }

  // DSP input interface.
  dsp.io.start := state === Launch
  dsp.io.mask  := RegEnable(io.mask, 0.U(bands.W), acceptProcess)

  // DSP -> output register boundary.
  io.streamOut := RegEnable(store.io.writeData.asUInt, 0.U(width.W), store.io.writeEn)

  // Speaker control
  speaker.io.start := state === Playback

  io.audPwm := speaker.io.audPwm
  io.audSd  := speaker.io.audSd

  switch(state) {
    is(Idle) {
      when(acceptStart) {
        recordingAddr := 0.U
        frameStart := 0.U
        hasRecording := false.B
        state := Capture
      }.elsewhen(acceptProcess) {
        frameStart := 0.U
        state := LoadFrame
      }.elsewhen(acceptOutput) {
        state := Playback
      }
    }

    is(Capture) {
      when(writeSample) {
        when(recordingAddr === lastRecordingAddr) {
          state := RecordingDone
        }.otherwise {
          recordingAddr := recordingAddr + 1.U
        }
      }
    }

    is(RecordingDone) {
      hasRecording := true.B
      state := Idle
    }

    is(LoadFrame) {
      when(loader.io.done) {
        state := Launch
      }
    }

    is(Launch) {
      state := WaitForDsp
    }

    is(WaitForDsp) {
      when(dsp.io.done) {
        state := StoreFrame
      }
    }

    is(StoreFrame) {
      when(store.io.done) {
        // Continue until the entire recording has been processed.
        when(frameStart === lastFrameStart) {
          state := ProcessingDone
        }.otherwise {
          frameStart := frameStart + samples.U
          state := LoadFrame
        }
      }
    }

    is(ProcessingDone) {
      // All processed samples are stored; wait for the output button.
      state := Idle
    }

    is(Playback) {
      when(speaker.io.done) {
        state := Idle
      }
    }
  }
}

object AudioTop extends App {
  val samples = sys.props.get("samples").map(_.toInt).getOrElse(32)
  val width = sys.props.get("width").map(_.toInt).getOrElse(8)
  val sampleRate = sys.props.get("sampleRate").map(_.toInt).getOrElse(12500)
  val recordingSamples = sys.props.get("recordingSamples").map(_.toInt).getOrElse(((sampleRate + samples - 1) / samples) * samples)
  val clkFreq = sys.props.get("clkFreq").map(_.toInt).getOrElse(100000000)
  val bands = sys.props.get("bands").map(_.toInt).getOrElse(16)
  emitVerilog(new AudioTop(samples, width, sampleRate, recordingSamples, clkFreq, bands), Array("--target-dir", "generated"))
}
