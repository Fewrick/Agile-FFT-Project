package fft

import chisel3._
import chisel3.experimental.{annotate, ChiselAnnotation}
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

    // P=1 parity banking: flipping any one logical address bit changes bank.
    // For future P=2^p routing: bank=(parity(a >> p) << p) | lowBits(a,p),
    // row=a >> (p+1). Keep routing separate from the butterfly arithmetic.
    def bank(address: UInt): Bool = address.xorR
    def row(address: UInt): UInt =
        if (samples == 2) 0.U(1.W) else address(addrWidth - 1, 1)

    // ReadFirst makes FIRRTL emit a registered data output, which Vivado can
    // map to BRAM. Correctness still forbids same-row read/write collisions.
    val bank0 = SyncReadMem(samples / 2, new ComplexSample(workingWidth), SyncReadMem.ReadFirst)
    val bank1 = SyncReadMem(samples / 2, new ComplexSample(workingWidth), SyncReadMem.ReadFirst)
    // Vivado otherwise chooses distributed RAM for these shallow banks.
    Seq(bank0, bank1).foreach { memory =>
        annotate(new ChiselAnnotation {
            override def toFirrtl =
                firrtl.AttributeAnnotation(memory.toTarget, "ram_style = \"block\"")
        })
    }
    val rowWidth = (addrWidth - 1) max 1
    val readEnable = WireDefault(VecInit(Seq.fill(2)(false.B)))
    val readRow = WireDefault(VecInit(Seq.fill(2)(0.U(rowWidth.W))))
    val writeEnable = WireDefault(VecInit(Seq.fill(2)(false.B)))
    val writeRow = WireDefault(VecInit(Seq.fill(2)(0.U(rowWidth.W))))
    val writeData = Wire(Vec(2, new ComplexSample(workingWidth)))
    writeData.foreach(_ := 0.U.asTypeOf(new ComplexSample(workingWidth)))

    // Exactly one read accessor and one whole-complex-word write per bank.
    val readData = VecInit(Seq(bank0.read(readRow(0), readEnable(0)),
                               bank1.read(readRow(1), readEnable(1))))
    when(writeEnable(0)) { bank0.write(writeRow(0), writeData(0)) }
    when(writeEnable(1)) { bank1.write(writeRow(1), writeData(1)) }
    for (b <- 0 until 2) {
        when(readEnable(b) && writeEnable(b)) {
            assert(readRow(b) =/= writeRow(b), "undefined memory read/write collision")
        }
    }

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

    // -------------------------
    // Input loading
    // -------------------------

    when(state === LOAD && io.in.fire) {

        // DIT FFT requires bit-reversed input ordering
        val address = Reverse(inputIndex)

        writeEnable(bank(address)) := true.B
        writeRow(bank(address)) := row(address)
        writeData(bank(address)) := io.in.data

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
        val bankA = Bool()
        val stage = UInt(log2Ceil(stages max 2).W)
        val last = Bool()
    })
    tag.addrA := addrA
    tag.addrB := addrB
    tag.bankA := bank(addrA)
    tag.stage := stage
    tag.last := butterflyIndex === lastButterfly

    when(issue) {
        assert(bank(addrA) =/= bank(addrB), "butterfly bank conflict")
        readEnable.foreach(_ := true.B)
        readRow(bank(addrA)) := row(addrA)
        readRow(bank(addrB)) := row(addrB)
    }

    // E0: Issue synchronous reads; capture routing, twiddles, and destinations.
    val valid0 = RegNext(issue, false.B)
    val tag0 = RegEnable(tag, issue)
    // The memory output is the E0 operand register; do not register it again.
    val a0 = readData(tag0.bankA)
    val b0 = readData(!tag0.bankA)
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
            when(tag4.stage === s.U) {
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
    val resultA = Wire(new ComplexSample(workingWidth))
    val resultB = Wire(new ComplexSample(workingWidth))
    resultA.re := finishButterfly(a4.re +& tr4)
    resultA.im := finishButterfly(a4.im +& ti4)
    resultB.re := finishButterfly(a4.re -& tr4)
    resultB.im := finishButterfly(a4.im -& ti4)

    when(valid4) {
        assert(stage === tag4.stage, "stage advanced before pipeline drained")
        assert(tag4.bankA === bank(tag4.addrA))
        assert(tag4.bankA =/= bank(tag4.addrB))
        writeEnable.foreach(_ := true.B)
        writeRow(tag4.bankA) := row(tag4.addrA)
        writeRow(!tag4.bankA) := row(tag4.addrB)
        writeData(tag4.bankA) := resultA
        writeData(!tag4.bankA) := resultB

        when(tag4.last) {
            assert(state === DRAIN && !issue)
            assert(!valid0 && !valid1 && !valid2 && !valid3,
                "stage completed with outstanding butterflies")
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

    // Prefetch bin zero on the final writeback edge, preserving the original
    // first-valid cycle. Later reads repeat the current bin during stalls, or
    // fetch its successor on a handshake. Thus the synchronous output remains
    // stable under backpressure without adding a second sample buffer.
    val firstOutput = valid4 && tag4.last && tag4.stage === lastStage
    val outputRead = firstOutput || state === OUTPUT
    val outputAddress = Mux(firstOutput, 0.U,
        Mux(io.out.fire, outputIndex + 1.U, outputIndex))
    val outputBank = RegEnable(bank(outputAddress), outputRead)

    // For N=2, bin zero is written on the prefetch edge. Forward the complete
    // result and suppress the colliding read instead of relying on BRAM mode.
    val forwardFirst = if (samples == 2) firstOutput else false.B
    if (samples == 2) {
        val forwarded = RegEnable(resultA, forwardFirst)
        val useForwarded = RegNext(forwardFirst, false.B)
        io.out.data := Mux(useForwarded, forwarded, readData(outputBank))
    } else {
        io.out.data := readData(outputBank)
    }
    when(outputRead && !forwardFirst) {
        readEnable(bank(outputAddress)) := true.B
        readRow(bank(outputAddress)) := row(outputAddress)
    }

    when(state === OUTPUT && io.out.fire) {
        when(outputIndex === lastIndex) {
            inputIndex := 0.U
            state := LOAD
        }.otherwise {
            outputIndex := outputIndex + 1.U
        }
    }
}
