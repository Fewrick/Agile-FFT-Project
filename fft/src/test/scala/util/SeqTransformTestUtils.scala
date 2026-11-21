package util

import chisel3._
import chisel3.util.log2Ceil
import chiseltest._
import cosimulation.{ComplexValue, FFTRadix2}
import org.scalatest.Assertions
import _root_.util.SignalTestUtils.wrapSigned

object SeqTransformTestUtils extends Assertions {
  def frameCycles(samples: Int): Int =
    2 * samples + log2Ceil(samples) * (samples / 2 + 5)

  def frameTimeout(samples: Int): Int = frameCycles(samples) + 4 * samples + 100

  // Check one frame; callers supply filename/frame context with withClue.
  def checkFrame(dut: SeqTransformHarness, input: Seq[ComplexValue], width: Int,
                 samples: Int, inverse: Boolean, useStalls: Boolean): Unit = {
    val expected = if (inverse) {
      FFTRadix2.ifftComplex(input).map(value =>
        ComplexValue(wrapSigned(value.re, width), wrapSigned(value.im, width)))
    } else FFTRadix2.fftComplex(input)
    var cycles = 0
    def step(): Unit = {
      dut.clock.step()
      cycles += 1
    }
    dut.io.out.ready.poke(true.B)
    for ((value, index) <- input.zipWithIndex) {
      if (useStalls && index % 3 == 0) {
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
    val expectedCycles = frameCycles(samples)
    val maxCycles = frameTimeout(samples)
    for ((value, index) <- expected.zipWithIndex) {
      withClue(s"output bin $index: ") {
        while (!dut.io.out.valid.peek().litToBoolean && cycles < maxCycles) {
          dut.io.in.ready.expect(false.B)
          step()
        }
        assert(dut.io.out.valid.peek().litToBoolean,
          s"Timed out after $cycles cycles (limit $maxCycles)")
        dut.io.in.ready.expect(false.B)
        dut.io.out.data.re.expect(value.re.S)
        dut.io.out.data.im.expect(value.im.S)
        if (useStalls) {
          dut.io.out.ready.poke(false.B)
          for (_ <- 0 until (index % 3 + 1)) {
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
    }
    dut.io.out.valid.expect(false.B)
    dut.io.in.ready.expect(true.B)
    if (!useStalls) assert(cycles == expectedCycles,
      s"Expected $expectedCycles cycles, got $cycles")
  }
}
