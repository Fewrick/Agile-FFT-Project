package fft

import chisel3._
import chisel3.util._
import interfaces._

class FFTSeq(val inputWidth: Int, val samples: Int) extends Module {
    require(inputWidth > 0)
    require(samples >= 2 && isPow2(samples))

    val SCALE = 127
    val stages = log2Ceil(samples)
    val outputWidth = inputWidth + stages
    val addrWidth = log2Ceil(samples)

    val io = IO(new Bundle {
        val in = Flipped(new DSPIO(SInt(inputWidth.W)))
        val out = new DSPIO(new ComplexSample(outputWidth))
    })

    // -------------------------
    // Working memory
    // -------------------------

    val buffer = Reg(Vec(samples, new ComplexSample(outputWidth)))

    // -------------------------
    // FSM
    // -------------------------

    object State extends ChiselEnum {
        val LOAD, COMPUTE, OUTPUT = Value
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

        buffer(address).re := io.in.data
        buffer(address).im := 0.S

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

    when(state === COMPUTE) {
        for (s <- 0 until stages) {
            when(stage === s.U) {
                val half = 1 << s
                val fftSize = 2 * half

                // Width of inputs at this stage
                val stageWidth = inputWidth + s

                // Butterfly indices
                val group = butterflyIndex / half.U
                val k = butterflyIndex % half.U

                val addrA = group * fftSize.U + k
                val addrB = addrA + half.U

                // Stage-specific twiddle factors
                val cosTable = VecInit(
                    (0 until half).map { j =>
                        val angle = 2 * math.Pi * j / fftSize
                        math.round(math.cos(angle) * SCALE).toInt.S(8.W)
                    }
                )

                val sinTable = VecInit(
                    (0 until half).map { j =>
                        val angle = 2 * math.Pi * j / fftSize
                        math.round(-math.sin(angle) * SCALE).toInt.S(8.W)
                    }
                )

                val cosFactor = cosTable(k)
                val sinFactor = sinTable(k)

                // Read complex operands.
                // Restrict widths to match FFTComb's
                // recursive intermediate widths.
                val ar = buffer(addrA).re(stageWidth - 1, 0).asSInt
                val ai = buffer(addrA).im(stageWidth - 1, 0).asSInt
                val br = buffer(addrB).re(stageWidth - 1, 0).asSInt
                val bi = buffer(addrB).im(stageWidth - 1, 0).asSInt

                // Same arithmetic as FFTComb
                val tr = ((cosFactor * br) -& (sinFactor * bi)) / SCALE.S(8.W)
                val ti = ((cosFactor * bi) +& (sinFactor * br)) / SCALE.S(8.W)

                // Butterfly results
                val outAr = ar +& tr
                val outAi = ai +& ti

                val outBr = ar -& tr
                val outBi = ai -& ti

                val nextWidth = stageWidth + 1

                buffer(addrA).re := outAr(nextWidth - 1, 0).asSInt
                buffer(addrA).im := outAi(nextWidth - 1, 0).asSInt
                buffer(addrB).re := outBr(nextWidth - 1, 0).asSInt
                buffer(addrB).im := outBi(nextWidth - 1, 0).asSInt
            }
        }

        // Advance butterfly/stage counters
        when(butterflyIndex === lastButterfly) {
            butterflyIndex := 0.U
            when(stage === lastStage) {
                outputIndex := 0.U
                state := OUTPUT
            }.otherwise {
                stage := stage + 1.U
            }
        }.otherwise {
            butterflyIndex := butterflyIndex + 1.U
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