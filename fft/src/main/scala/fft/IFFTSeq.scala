package fft

import chisel3._
import chisel3.util._
import interfaces._

class IFFTSeq(val outputWidth: Int, val samples: Int) extends Module {
  require(outputWidth > 0)
  require(samples >= 2 && isPow2(samples))

  val SCALE = 127
  val stages = log2Ceil(samples)
  val inputWidth = outputWidth + stages
  val addrWidth = log2Ceil(samples)

  val io = IO(new Bundle {
    val in = Flipped(new DSPIO(new ComplexSample(inputWidth)))
    val out = new DSPIO(new ComplexSample(outputWidth))
  })



  // Memory to hold the input samples
  // Stores one complete frequency-domain frame and the intermediate results produced by the inverse FFT butterflies
  val buffer = Reg(Vec(samples, new ComplexSample(inputWidth)))

  // The FSM states for the IFFT operation
  // LOAD - Receive on complete frequency-domain frame
  // COMPUTE - Execute one radix-2 butterfly per clock cycle
  // OUTPUT - Stream the reconstructed time-domain frame
  object State extends ChiselEnum {
    val LOAD, COMPUTE, DRAIN, OUTPUT = Value
  }
  import State._

  val state = RegInit(LOAD)
  val inputIndex = RegInit(0.U(addrWidth.W))
  val outputIndex = RegInit(0.U(addrWidth.W))
  val stage = RegInit(0.U(log2Ceil(stages max 2).W))
  val butterflyIndex = RegInit(0.U(log2Ceil((samples / 2) max 2).W))

  val lastIndex = (samples - 1).U
  val lastStage = (stages - 1).U
  val lastButterfly = (samples / 2 - 1).U


  // Streaming interface signals

  // The module accepts input only while laoding a frame
  io.in.ready := state === LOAD

  // Output remans valid until the downstream module accepts it
  io.out.valid := state === OUTPUT
  io.out.data.re := buffer(outputIndex).re(outputWidth - 1, 0).asSInt
  io.out.data.im := buffer(outputIndex).im(outputWidth - 1, 0).asSInt


  // Frequency-domain frame loading

  when(state === LOAD && io.in.fire) {
    val address = Reverse(inputIndex)

    buffer(address).re := io.in.data.re
    buffer(address).im := io.in.data.im

    when(inputIndex === lastIndex) {
      stage := 0.U
      butterflyIndex := 0.U
      state := COMPUTE
    }.otherwise {
      inputIndex := inputIndex + 1.U
    }
  }

  // Inverse FFT computation

  // Shared address generation. A and B differ in bit `stage`.
  val half = UIntToOH(stage, addrWidth)
  val lowMask = half - 1.U
  val index = butterflyIndex.pad(addrWidth)
  val k = index & lowMask

  val addrA = ((index & ~lowMask) << 1)(addrWidth - 1, 0) | k
  val addrB = addrA | half

  // Map each stage's twiddle to the full-size table.
  // The inverse transform uses positive sine.
  val twiddleAddrWidth = log2Ceil((samples / 2) max 2)
  val twiddleIndex = (k << (lastStage - stage))(twiddleAddrWidth - 1, 0)

  val cosTable = VecInit((0 until (samples / 2)).map { j =>
    val angle = 2 * math.Pi * j / samples
    math.round(math.cos(angle) * SCALE).toInt.S(8.W)
  })

  val sinTable = VecInit((0 until (samples / 2)).map { j =>
    val angle = 2 * math.Pi * j / samples
    math.round(math.sin(angle) * SCALE).toInt.S(8.W)
  })

  val cosFactor = if (samples == 2) cosTable(0) else cosTable(twiddleIndex)
  val sinFactor = if (samples == 2) sinTable(0) else sinTable(twiddleIndex)

  val issue = state === COMPUTE

  val tag = Wire(new Bundle {
    val addrA = UInt(addrWidth.W)
    val addrB = UInt(addrWidth.W)
    val last = Bool()
  })
  tag.addrA := addrA
  tag.addrB := addrB
  tag.last := butterflyIndex === lastButterfly

  // E0: Register operands, twiddle, and writeback information.
  val valid0 = RegNext(issue, false.B)
  val tag0 = RegEnable(tag, issue)
  val a0 = RegEnable(buffer(addrA), issue)
  val b0 = RegEnable(buffer(addrB), issue)
  val cos0 = RegEnable(cosFactor, issue)
  val sin0 = RegEnable(sinFactor, issue)

  // E1: Register the four signed products.
  val valid1 = RegNext(valid0, false.B)
  val tag1 = RegEnable(tag0, valid0)
  val a1 = RegEnable(a0, valid0)
  val reCos1 = RegEnable(b0.re * cos0, valid0)
  val imSin1 = RegEnable(b0.im * sin0, valid0)
  val imCos1 = RegEnable(b0.im * cos0, valid0)
  val reSin1 = RegEnable(b0.re * sin0, valid0)

  // E2: Combine the products at full precision.
  val valid2 = RegNext(valid1, false.B)
  val tag2 = RegEnable(tag1, valid1)
  val a2 = RegEnable(a1, valid1)
  val productRe2 = RegEnable(reCos1 -& imSin1, valid1)
  val productIm2 = RegEnable(imCos1 +& reSin1, valid1)

  // E3: Scale the complex product, preserving signed division.
  val valid3 = RegNext(valid2, false.B)
  val tag3 = RegEnable(tag2, valid2)
  val a3 = RegEnable(a2, valid2)
  val tr3 = RegEnable(productRe2 / SCALE.S(8.W), valid2)
  val ti3 = RegEnable(productIm2 / SCALE.S(8.W), valid2)

  // A signed shift rounds negative odd values down. Add one in that case
  // to match the original signed /2, which truncates toward zero.
  def normalize(value: SInt): SInt = {
    val correction = Mux(value < 0.S && value(0), 1.S(2.W), 0.S(2.W))
    val scaled = (value >> 1) + correction
    scaled(inputWidth - 1, 0).asSInt
  }

  when(issue) {
    when(tag.last) {
      state := DRAIN
    }.otherwise {
      butterflyIndex := butterflyIndex + 1.U
    }
  }

  // E4: Normalize and write both outputs. Advance on the final writeback.
  when(valid3) {
    buffer(tag3.addrA).re := normalize(a3.re +& tr3)
    buffer(tag3.addrA).im := normalize(a3.im +& ti3)
    buffer(tag3.addrB).re := normalize(a3.re -& tr3)
    buffer(tag3.addrB).im := normalize(a3.im -& ti3)

    when(tag3.last) {
      butterflyIndex := 0.U
      when(stage === lastStage) {
        outputIndex := 0.U
        state := OUTPUT
      }.otherwise {
        stage := stage + 1.U
        state := COMPUTE
      }
    }
  }

  // Time-domain outout streaming

  // Hold the current output until the downstream consumer accepts it
  when(state === OUTPUT && io.out.fire) {
    when(outputIndex === lastIndex) {
      inputIndex := 0.U
      state := LOAD
    }.otherwise {
      outputIndex := outputIndex + 1.U
    }
  }
}
