package top

import audioIO.Microphone
import chisel3._
import chisel3.util._

class AudioCaptureTop(val captureSamples: Int = 12000, val capacity: Int = 16384, val width: Int = 8) extends Module {
  require(capacity >= 2 && isPow2(capacity))
  require(width == 8)

  val io = IO(new Bundle {
    val streamIn = Input(Bool())
    val captureButton = Input(Bool())
    val micClkOut = Output(Bool())
    val captureBusy = Output(Bool())
    val captureDone = Output(Bool())
    val readAddr = Input(UInt(log2Ceil(capacity).W))
    val readEn = Input(Bool())
    val readData = Output(SInt(width.W))
  })

  val microphone = Module(new Microphone(width))
  val capture = Module(new AudioCapture(captureSamples, capacity, width))

  microphone.io.micEn := true.B
  microphone.io.micDataStream := io.streamIn
  io.micClkOut := microphone.io.micClock

  val buttonSync = RegNext(RegNext(io.captureButton, false.B), false.B)
  val buttonRise = buttonSync && !RegNext(buttonSync, false.B)
  capture.io.start := buttonRise
  capture.io.sample := microphone.io.writeData
  capture.io.sampleValid := microphone.io.writeEn
  capture.io.readAddr := io.readAddr
  capture.io.readEn := io.readEn
  io.readData := capture.io.readData
  io.captureBusy := capture.io.busy
  io.captureDone := capture.io.done
}

object AudioCaptureTop extends App {
  emitVerilog(new AudioCaptureTop(), Array("--target-dir", "generated"))
}
