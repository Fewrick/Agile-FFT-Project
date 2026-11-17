package fft

import chisel3._
import chisel3.util._
import interfaces._
import arithmetic._

/** Sequential radix-2 transform with full-width complex streaming IO.
  * Both ports and the working buffer use baseWidth + log2Ceil(samples) bits.
  * Direction is fixed at elaboration; forward stages grow by one bit while
  * inverse stages normalize by two with truncation toward zero.
  */
class Radix2Seq(val baseWidth: Int, val samples: Int, val inverse: Boolean) extends Module {
    require(baseWidth > 0)
    require(samples >= 2 && isPow2(samples))

    val SCALE = 127
    val stages = log2Ceil(samples)
    val workingWidth = baseWidth + stages
    val addrWidth = log2Ceil(samples)

    val io = IO(new Bundle {
        val in = Flipped(new DSPIO(new ComplexSample(workingWidth)))
        val out = new DSPIO(new ComplexSample(workingWidth))
    })

    // -------------------------
    // Working memory
    // -------------------------

    val buffer = Reg(Vec(samples, new ComplexSample(workingWidth)))

    // -------------------------
    // FSM
    // -------------------------

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

    // -------------------------
    // Streaming interface
    // -------------------------

    io.in.ready := state === LOAD

    io.out.valid := state === OUTPUT
    io.out.data := buffer(outputIndex)

    // -------------------------
    // Input loading
    // -------------------------

    when(state === LOAD && io.in.fire) {

        // DIT FFT requires bit-reversed input ordering
        val address = Reverse(inputIndex)

        buffer(address) := io.in.data

        when(inputIndex === lastIndex) {
            stage := 0.U
            butterflyIndex := 0.U
            state := COMPUTE
        }.otherwise {
            inputIndex := inputIndex + 1.U
        }
    }

    // -------------------------
    // FFT computation
    // -------------------------

    // Insert a zero at bit `stage` to form A, then set that bit to form B.
    val half = UIntToOH(stage, addrWidth)
    val lowMask = half - 1.U
    val index = butterflyIndex.pad(addrWidth)
    val k = index & lowMask

    val addrA = ((index & ~lowMask) << 1)(addrWidth - 1, 0) | k
    val addrB = addrA | half

    // Map the stage-local twiddle to the full-size FFT table.
    val twiddleIndexWidth = log2Ceil((samples / 2) max 2)
    val twiddleIndex = (k << (lastStage - stage))(twiddleIndexWidth - 1, 0)

    val cosTable = VecInit((0 until samples / 2).map { j =>
        val angle = 2 * math.Pi * j / samples
        math.round(math.cos(angle) * SCALE).toInt.S(8.W)
    })

    val sinTable = VecInit((0 until samples / 2).map { j =>
        val angle = 2 * math.Pi * j / samples
        // Apply the sign before rounding to preserve the original coefficients.
        val sine = if (inverse) math.sin(angle) else -math.sin(angle)
        math.round(sine * SCALE).toInt.S(8.W)
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

    // E0: Capture the selected operands, twiddle, and destination addresses.
    val valid0 = RegNext(issue, false.B)
    val tag0 = RegEnable(tag, issue)
    val a0 = RegEnable(buffer(addrA), issue)
    val b0 = RegEnable(buffer(addrB), issue)
    val cos0 = RegEnable(cosFactor, issue)
    val sin0 = RegEnable(sinFactor, issue)

    // E1: Capture the four signed products.
    val valid1 = RegNext(valid0, false.B)
    val tag1 = RegEnable(tag0, valid0)
    val a1 = RegEnable(a0, valid0)
    val reCos1 = RegEnable(b0.re * cos0, valid0)
    val imSin1 = RegEnable(b0.im * sin0, valid0)
    val imCos1 = RegEnable(b0.im * cos0, valid0)
    val reSin1 = RegEnable(b0.re * sin0, valid0)

    // E2: Combine the products without losing precision.
    val valid2 = RegNext(valid1, false.B)
    val tag2 = RegEnable(tag1, valid1)
    val a2 = RegEnable(a1, valid1)
    val productRe2 = RegEnable(reCos1 -& imSin1, valid1)
    val productIm2 = RegEnable(imCos1 +& reSin1, valid1)

    // E3: Preserve the original signed division and truncation toward zero.
    val valid3 = RegNext(valid2, false.B)
    val tag3 = RegEnable(tag2, valid2)
    val a3 = RegEnable(a2, valid2)

    val divRe = Module(new Div127(productRe2.getWidth, true))
    val divIm = Module(new Div127(productIm2.getWidth, true))

    divRe.io.in := productRe2
    divIm.io.in := productIm2

    // E4: Register the corrected signed quotients and their writeback data.
    val valid4 = RegNext(valid3, false.B)
    val tag4 = RegEnable(tag3, valid3)
    val a4 = RegEnable(a3, valid3)
    val tr4 = RegEnable(divRe.io.out, valid3)
    val ti4 = RegEnable(divIm.io.out, valid3)

    // Truncate to baseWidth + stage + 1 bits, then sign-extend to the buffer width.
    def wrapForStage(value: SInt): SInt = {
        val wrapped = Wire(SInt(workingWidth.W))
        wrapped := value(workingWidth - 1, 0).asSInt

        for (s <- 0 until (stages - 1)) {
            when(stage === s.U) {
                wrapped := value(baseWidth + s, 0).asSInt
            }
        }
        wrapped
    }

    // Signed /2 truncates toward zero, including negative odd results.
    def normalize(value: SInt): SInt = {
        val correction = Mux(value < 0.S && value(0), 1.S(2.W), 0.S(2.W))
        val scaled = (value >> 1) + correction
        scaled(workingWidth - 1, 0).asSInt
    }

    // Direction is fixed at elaboration; no runtime direction mux is needed.
    def finishButterfly(value: SInt): SInt = {
        if (inverse) normalize(value) else wrapForStage(value)
    }

    when(issue) {
        when(tag.last) {
            state := DRAIN
        }.otherwise {
            butterflyIndex := butterflyIndex + 1.U
        }
    }

    // E5: Write both results. The final writeback releases the next stage.
    when(valid4) {
        buffer(tag4.addrA).re := finishButterfly(a4.re +& tr4)
        buffer(tag4.addrA).im := finishButterfly(a4.im +& ti4)
        buffer(tag4.addrB).re := finishButterfly(a4.re -& tr4)
        buffer(tag4.addrB).im := finishButterfly(a4.im -& ti4)

        when(tag4.last) {
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

    // -------------------------
    // Output streaming
    // -------------------------

    when(state === OUTPUT && io.out.fire) {
        when(outputIndex === lastIndex) {
            inputIndex := 0.U
            state := LOAD
        }.otherwise {
            outputIndex := outputIndex + 1.U
        }
    }
}
