import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import fft.FFT

class FFTtest extends AnyFlatSpec with ChiselScalatestTester {
  "FFT" should "pass" in {
    test(new FFT()) { dut =>
      for (i <- 0 until 32) {
        dut.io.input(i).poke(i.U)
      }
      for (i <- 0 until 32) {
        dut.io.output(i).expect(i.U)
      }
    }
  }
}

