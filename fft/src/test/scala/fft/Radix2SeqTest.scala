package fft

import chisel3._
import chisel3.util._
import chiseltest._
import cosimulation.{ComplexValue, FFTRadix2}
import interfaces._
import org.scalatest.flatspec.AnyFlatSpec

// Exercise the public wrappers through one full-width complex test interface.
class SeqTransformHarness(width: Int, samples: Int, inverse: Boolean) extends Module {
  val fullWidth = width + log2Ceil(samples)
  val io = IO(new Bundle {
    val in = Flipped(new DSPIO(new ComplexSample(fullWidth)))
    val out = new DSPIO(new ComplexSample(fullWidth))
  })
  if (inverse) {
    val engine = Module(new IFFTSeq(width, samples))
    engine.io.in <> io.in
    io.out.data.re := engine.io.out.data.re
    io.out.data.im := engine.io.out.data.im
    io.out.valid := engine.io.out.valid
    engine.io.out.ready := io.out.ready
  } else {
    val engine = Module(new FFTSeq(width, samples))
    engine.io.in.data := io.in.data.re(width - 1, 0).asSInt
    engine.io.in.valid := io.in.valid
    io.in.ready := engine.io.in.ready
    io.out <> engine.io.out
  }
}

class Radix2SeqTest extends AnyFlatSpec with ChiselScalatestTester {
  private def wrap(value: Int, width: Int): Int = {
    val mask = (1L << width) - 1
    val bits = value.toLong & mask
    val signBit = 1L << (width - 1)

    (if ((bits & signBit) != 0) bits - (1L << width) else bits).toInt
  }

  for (inverse <- Seq(false, true); samples <- Seq(32, 64, 128)) {
    val width = 8
    val direction = if (inverse) "IFFTSeq" else "FFTSeq"
    direction should s"match exact wrapped results at width $width, size $samples, with stalls and repeated frames" in {
      test(new SeqTransformHarness(width, samples, inverse)) { dut =>
        val random = new scala.util.Random(42)
        val dataWidth = width + (if (inverse) log2Ceil(samples) else 0)
        val limit = 1 << (dataWidth - 1)

        val frames = {
          val basicFrames = Seq(
            // Alternating extremes
            Seq.tabulate(samples)(i =>
              ComplexValue(
                if (i % 2 == 0) -limit else limit - 1,
                if (inverse) (if (i % 3 == 0) -limit else limit - 1) else 0
              )
            ),

            // Random full-range input
            Seq.fill(samples)(
              ComplexValue(
                random.nextInt(2 * limit) - limit,
                if (inverse) random.nextInt(2 * limit) - limit else 0
              )
            ),

            // Negative impulse
            Seq.tabulate(samples)(i =>
              ComplexValue(
                if (i == 0) -1 else 0,
                if (inverse && i == 1) -3 else 0
              )
            )
          )

          val additionalFrames = {
            val amplitude = if (inverse) 16 else math.min(16, limit - 1)

            Seq(
              // All zeros
              Seq.fill(samples)(ComplexValue(0, 0)),

              // Constant signal
              Seq.fill(samples)(ComplexValue(amplitude, 0)),

              // Positive impulse
              Seq.tabulate(samples)(i => ComplexValue(if (i == 0) amplitude else 0, 0)),

              // Impulse in the middle
              Seq.tabulate(samples)(i => ComplexValue(if (i == samples / 2) amplitude else 0, 0)),

              // Alternating signs
              Seq.tabulate(samples)(i => ComplexValue(if (i % 2 == 0) amplitude else -amplitude, 0)),

              // Single-frequency cosine
              Seq.tabulate(samples)(i => ComplexValue(math.round(amplitude * math.cos(2 * math.Pi * 3 * i / samples)).toInt, 0)),

              // Single-frequency sine
              Seq.tabulate(samples)(i => ComplexValue(math.round(amplitude * math.sin(2 * math.Pi * 5 * i / samples)).toInt, 0)),

              // Multiple frequencies
              Seq.tabulate(samples)(i => ComplexValue(math.round((amplitude / 2.0) * (math.sin(2 * math.Pi * 2 * i / samples) + math.cos(2 * math.Pi * 7 * i / samples))).toInt, 0)),

              // Random low-amplitude input
              Seq.fill(samples)(ComplexValue(random.nextInt(2 * amplitude + 1) - amplitude,
                  if (inverse) random.nextInt(2 * amplitude + 1) - amplitude else 0)),

              // Complex sinusoid (IFFT only)
              Seq.tabulate(samples)(i =>
                if (inverse) {
                  val angle = 2 * math.Pi * 4 * i / samples
                  ComplexValue(
                    math.round(amplitude * math.cos(angle)).toInt,
                    math.round(amplitude * math.sin(angle)).toInt
                  )
                } else {
                  ComplexValue(0, 0)
                }
              )
            )
          }

          basicFrames ++ additionalFrames
        }

        for ((input, frame) <- frames.zipWithIndex) {
          var cycles = 0
          def step(): Unit = { dut.clock.step(); cycles += 1 }
          dut.io.out.ready.poke(true.B)
          for ((value, i) <- input.zipWithIndex) {
            if (frame == 1 && i % 3 == 0) {
              dut.io.in.valid.poke(false.B)
              dut.io.in.ready.expect(true.B)
              step()
            }
            dut.io.in.valid.poke(true.B)
            dut.io.in.data.re.poke(value.re.S)
            dut.io.in.data.im.poke(value.im.S)
            dut.io.in.ready.expect(true.B)
            step()
          }
          dut.io.in.valid.poke(false.B)
          val expected =
            if (inverse) {
              FFTRadix2.ifftComplex(input).map { v => ComplexValue(wrap(v.re, width), wrap(v.im, width)) }
            } else {
              FFTRadix2.fftComplex(input)
            }
          for ((value, i) <- expected.zipWithIndex) {
            while (!dut.io.out.valid.peek().litToBoolean && cycles < 1000) {
              dut.io.in.ready.expect(false.B)
              step()
            }
            dut.io.out.valid.expect(true.B)
            dut.io.in.ready.expect(false.B)
            dut.io.out.data.re.expect(value.re.S)
            dut.io.out.data.im.expect(value.im.S)
            if (frame == 1) {
              dut.io.out.ready.poke(false.B)
              for (_ <- 0 until (i % 3 + 1)) {
                step()
                dut.io.out.valid.expect(true.B)
                dut.io.in.ready.expect(false.B)
                dut.io.out.data.re.expect(value.re.S)
                dut.io.out.data.im.expect(value.im.S)
              }
              dut.io.out.ready.poke(true.B)
            }
            step()
          }
          dut.io.out.valid.expect(false.B)
          dut.io.in.ready.expect(true.B)
          if (frame != 1) {
            assert(cycles == 2 * samples + log2Ceil(samples) * (samples / 2 + 5))
            if (samples == 32) assert(cycles == 169)
          }
        }
      }
    }
  }
}
