package fft

import chisel3._
import chisel3.util._
import chiseltest._
import cosimulation.ComplexValue
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
  private def wrap(value: Long, width: Int): Int = {
    val modulus = 1L << width
    val bits = value & (modulus - 1)
    (if (bits >= modulus / 2) bits - modulus else bits).toInt
  }

  // Recursive reference models each stage's original wrap and signed division.
  private def reference(input: Seq[ComplexValue], width: Int, inverse: Boolean): Seq[ComplexValue] = {
    val fullWidth = width + log2Ceil(input.size)
    def transform(x: Seq[ComplexValue]): Seq[ComplexValue] = {
      if (x.size == 1) x
      else {
        val even = transform(x.indices.collect { case i if i % 2 == 0 => x(i) })
        val odd = transform(x.indices.collect { case i if i % 2 == 1 => x(i) })
        val resultWidth = if (inverse) fullWidth else width + log2Ceil(x.size)
        def finish(value: Long): Int = wrap(if (inverse) value / 2 else value, resultWidth)
        val pairs = (0 until x.size / 2).map { k =>
          val angle = 2 * math.Pi * k / x.size
          val cos = math.round(math.cos(angle) * 127)
          val sine = if (inverse) math.sin(angle) else -math.sin(angle)
          val sin = math.round(sine * 127)
          val re = (odd(k).re * cos - odd(k).im * sin) / 127
          val im = (odd(k).im * cos + odd(k).re * sin) / 127
          (ComplexValue(finish(even(k).re + re), finish(even(k).im + im)),
           ComplexValue(finish(even(k).re - re), finish(even(k).im - im)))
        }
        pairs.map(_._1) ++ pairs.map(_._2)
      }
    }
    transform(input).map(v => if (inverse) ComplexValue(wrap(v.re, width), wrap(v.im, width)) else v)
  }

  for (inverse <- Seq(false, true); samples <- Seq(2, 4, 32); width <- Seq(3, 8)) {
    val direction = if (inverse) "IFFTSeq" else "FFTSeq"
    direction should s"match exact wrapped results at width $width, size $samples, with stalls and repeated frames" in {
      test(new SeqTransformHarness(width, samples, inverse)) { dut =>
        val random = new scala.util.Random(42)
        val dataWidth = width + (if (inverse) log2Ceil(samples) else 0)
        val limit = 1 << (dataWidth - 1)
        val frames = Seq(
          Seq.tabulate(samples)(i => ComplexValue(if (i % 2 == 0) -limit else limit - 1,
            if (inverse) (if (i % 3 == 0) -limit else limit - 1) else 0)),
          Seq.fill(samples)(ComplexValue(random.nextInt(2 * limit) - limit,
            if (inverse) random.nextInt(2 * limit) - limit else 0)),
          Seq.tabulate(samples)(i => ComplexValue(if (i == 0) -1 else 0, if (inverse && i == 1) -3 else 0)))

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
          val expected = reference(input, width, inverse)
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
