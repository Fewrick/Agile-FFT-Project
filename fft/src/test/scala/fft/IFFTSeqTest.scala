package fft

import chisel3._
import chiseltest._
import interfaces._
import org.scalatest.flatspec.AnyFlatSpec
import cosimulation.FFTRadix2

class FFTIFFTSeq(val width: Int, val samples: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new DSPIO(SInt(width.W)))
    val out = new DSPIO(new ComplexSample(width))
  })

  val fft = Module(new FFTSeq(width, samples))
  val ifft = Module(new IFFTSeq(width, samples))

  fft.io.in <> io.in
  ifft.io.in <> fft.io.out
  io.out <> ifft.io.out
}

class IFFTSeqTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("IFFTSeq")

  it should "match the software inverse FFT" in {
    val samples = 32
    val outputWidth = 8
    val rand = new scala.util.Random(42)
    val inputRe = Seq.fill(samples)(rand.nextInt(128) - 64)
    val inputIm = Seq.fill(samples)(rand.nextInt(128) - 64)
    val (expectedRe, expectedIm) = FFTRadix2.ifftComplex(inputRe, inputIm)

    test(new IFFTSeq(outputWidth, samples)) { dut =>
      dut.io.out.ready.poke(true.B)

      for (i <- 0 until samples) {
        dut.io.in.valid.poke(true.B)
        dut.io.in.data.re.poke(inputRe(i).S)
        dut.io.in.data.im.poke(inputIm(i).S)
        while (!dut.io.in.ready.peek().litToBoolean) {
          dut.clock.step()
        }
        dut.clock.step()
      }
      dut.io.in.valid.poke(false.B)

      for (i <- 0 until samples) {
        while (!dut.io.out.valid.peek().litToBoolean) {
          dut.clock.step()
        }
        assert(math.abs(dut.io.out.data.re.peek().litValue.toInt - expectedRe(i)) <= 2)
        assert(math.abs(dut.io.out.data.im.peek().litValue.toInt - expectedIm(i)) <= 2)
        dut.clock.step()
      }
    }
  }

  it should "round trip a frame through FFTSeq and IFFTSeq under backpressure" in {
    val samples = 32
    val width = 8
    val input = Seq.tabulate(samples)(i => ((i * 13) % 127) - 63)

    test(new FFTIFFTSeq(width, samples)) { dut =>
      dut.io.out.ready.poke(true.B)

      for (value <- input) {
        dut.io.in.valid.poke(true.B)
        dut.io.in.data.poke(value.S)
        while (!dut.io.in.ready.peek().litToBoolean) {
          dut.clock.step()
        }
        dut.clock.step()
      }
      dut.io.in.valid.poke(false.B)

      for (i <- input.indices) {
        dut.io.out.ready.poke(false.B)
        while (!dut.io.out.valid.peek().litToBoolean) {
          dut.clock.step()
        }
        assert(math.abs(dut.io.out.data.re.peek().litValue.toInt - input(i)) <= 2)
        assert(math.abs(dut.io.out.data.im.peek().litValue.toInt) <= 2)
        dut.io.out.ready.poke(true.B)
        dut.clock.step()
      }
    }
  }
}
