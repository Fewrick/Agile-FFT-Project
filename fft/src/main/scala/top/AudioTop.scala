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
    val process   = Input(Bool()) // Recompute FFT -> mask -> IFFT from the original recording
    val output    = Input(Bool()) // Play the current recording
    val mask      = Input(UInt(bands.W))
    val streamOut = Output(UInt(width.W)) // LED status: stored, processed, playing
    val micClkOut = Output(Bool())
    val audPwm    = Output(Bool())
    val audSd     = Output(Bool())
  })

  // Recording memory for captured samples
  val recordingMem = SyncReadMem(recordingSamples, SInt(width.W), SyncReadMem.ReadFirst)
  annotate(new ChiselAnnotation { override def toFirrtl = firrtl.AttributeAnnotation(recordingMem.toTarget, "ram_style = \"block\"") })
  val recordingAddr = RegInit(0.U(recordingAddrWidth.W))
  val lastRecordingAddr = (recordingSamples - 1).U

  // Processed memory for IFFT output samples
  val processedMem = SyncReadMem(recordingSamples, SInt(width.W), SyncReadMem.ReadFirst)
  annotate(new ChiselAnnotation { override def toFirrtl = firrtl.AttributeAnnotation(processedMem.toTarget, "ram_style = \"block\"") })

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
  val hasRecording = RegInit(false.B)
  val hasProcessed = RegInit(false.B)

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

  // One synchronous read port per RAM. Frame loading always uses the original;
  // playback selects the original or the completed processed recording.
  val recordingReadEn = WireDefault(loader.io.readEn || (speaker.io.readEn && !hasProcessed))
  val recordingReadAddr = WireDefault(Mux(loader.io.readEn, loader.io.readAddr, speaker.io.readAddr))
  val recordingReadData = recordingMem.read(recordingReadAddr, recordingReadEn)
  val processedReadEn = WireDefault(speaker.io.readEn && hasProcessed)
  val processedReadAddr = WireDefault(speaker.io.readAddr)
  val processedReadData = processedMem.read(processedReadAddr, processedReadEn)

  loader.io.readData := recordingReadData
  // hasProcessed stays constant throughout playback, including RAM responses.
  speaker.io.readData := Mux(hasProcessed, processedReadData, recordingReadData)

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
  // Simultaneous presses have deterministic priority: capture, process, output.
  // Busy presses are ignored rather than queued for a later operation.
  val acceptStart = state === Idle && startRise
  val acceptProcess = state === Idle && hasRecording && !startRise && processRise
  val acceptOutput = state === Idle && hasRecording && !startRise && !processRise && outputRise
  val writeSample = state === Capture && mic.io.writeEn

  // Only acquisition can modify the original; every processing pass writes
  // FrameStore's global destination addresses into the separate processed RAM.
  when(writeSample) {
    recordingMem.write(recordingAddr, mic.io.writeData)
  }
  when(store.io.writeEn) {
    processedMem.write(store.io.writeAddr, store.io.writeData)
  }

  // DSP input interface.
  dsp.io.start := state === Launch
  dsp.io.mask  := RegEnable(io.mask, 0.U(bands.W), acceptProcess)

  // LED0/1 persist until capture/reprocessing invalidates their status.
  // LED2 is active only while the speaker is busy; all other LEDs are off.
  io.streamOut := Cat(0.U((width - 3).W), speaker.io.busy, hasProcessed, hasRecording)

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
        hasProcessed := false.B
        state := Capture
      }.elsewhen(acceptProcess) {
        frameStart := 0.U
        hasProcessed := false.B
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
      hasProcessed := true.B
      state := Idle
    }

    is(Playback) {
      when(speaker.io.done) {
        state := Idle
      }
    }
  }

  // Assertions for simulation and formal verification
  assert(!(writeSample && store.io.writeEn), "Acquisition and frame storage must be exclusive")
  assert(!(recordingReadEn && writeSample), "Original RAM access phases must be exclusive")
  assert(!(processedReadEn && store.io.writeEn), "Processed RAM access phases must be exclusive")
  when(writeSample) {
    assert(state === Capture)
    assert(recordingAddr < recordingSamples.U)
  }
  when(store.io.writeEn) {
    assert(state === StoreFrame)
    assert(store.io.writeAddr < recordingSamples.U)
  }
  when(recordingReadEn) { 
    assert(recordingReadAddr < recordingSamples.U) 
  }
  when(processedReadEn) { 
    assert(processedReadAddr < recordingSamples.U) 
  }
  when(state === LoadFrame || state === Launch || state === WaitForDsp ||
    state === StoreFrame || state === ProcessingDone) {
    assert(!writeSample, "Processing must preserve the original recording")
  }
  when(loader.io.writeEn) { 
    assert(!dsp.io.busy && !dsp.io.start) 
  }
  when(dsp.io.start) { 
    assert(!dsp.io.busy && !loader.io.busy) 
  }
  when(store.io.readEn) { 
    assert(dsp.io.done) 
  }
  when(loader.io.done) { 
    assert(!loader.io.writeEn && !loader.io.busy) 
  }
  when(store.io.done) { 
    assert(!store.io.writeEn && !store.io.busy) 
  }
  when(state =/= Idle && state =/= Capture) {
    assert(frameStart <= lastFrameStart)
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
