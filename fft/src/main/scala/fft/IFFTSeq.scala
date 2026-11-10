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

  val buffer = Reg(Vec(samples, new ComplexSample(inputWidth)))

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

  io.in.ready := state === LOAD
  io.out.valid := state === OUTPUT
  io.out.data.re := buffer(outputIndex).re(outputWidth - 1, 0).asSInt
  io.out.data.im := buffer(outputIndex).im(outputWidth - 1, 0).asSInt

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

  when(state === COMPUTE) {
    for (s <- 0 until stages) {
      when(stage === s.U) {
        val half = 1 << s
        val fftSize = 2 * half

        val group = butterflyIndex / half.U
        val k = butterflyIndex % half.U

        val cosTable = VecInit(
          (0 until half).map { j =>
            val angle = 2 * math.Pi * j / fftSize
            math.round(math.cos(angle) * SCALE).toInt.S(8.W)
          }
        )

        val sinTable = VecInit(
          (0 until half).map { j =>
            val angle = 2 * math.Pi * j / fftSize
            math.round(math.sin(angle) * SCALE).toInt.S(8.W)
          }
        )

        val cosFactor = cosTable(k)
        val sinFactor = sinTable(k)

        val addrA = group * fftSize.U + k
        val addrB = addrA + half.U

        val ar = buffer(addrA).re
        val ai = buffer(addrA).im
        val br = buffer(addrB).re
        val bi = buffer(addrB).im

        val tr = ((cosFactor * br) -& (sinFactor * bi)) / SCALE.S(8.W)
        val ti = ((cosFactor * bi) +& (sinFactor * br)) / SCALE.S(8.W)

        val outAr = (ar +& tr) / 2.S
        val outAi = (ai +& ti) / 2.S
        val outBr = (ar -& tr) / 2.S
        val outBi = (ai -& ti) / 2.S

        buffer(addrA).re := outAr(inputWidth - 1, 0).asSInt
        buffer(addrA).im := outAi(inputWidth - 1, 0).asSInt
        buffer(addrB).re := outBr(inputWidth - 1, 0).asSInt
        buffer(addrB).im := outBi(inputWidth - 1, 0).asSInt
      }
    }

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

  when(state === OUTPUT && io.out.fire) {
    when(outputIndex === lastIndex) {
      inputIndex := 0.U
      state := LOAD
    }.otherwise {
      outputIndex := outputIndex + 1.U
    }
  }
}
