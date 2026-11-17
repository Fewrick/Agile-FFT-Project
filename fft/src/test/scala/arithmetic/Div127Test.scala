package arithmetic

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

class Div127Test extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Div127"

  for (width <- Seq(16, 22, 27)) {
    it should s"match signed division for $width-bit inputs" in {
      test(new Div127(width)) { dut =>
        val limit = 1 << (width - 1)
        val random = new Random(127L + width)

        val boundaries = Seq(-limit, -limit + 1, 0, limit - 2, limit - 1)
        val nearMultiples = Seq(-381, -254, -127, 127, 254, 381).flatMap { n =>
          Seq(n - 1, n, n + 1)
        }
        val randomInputs = Seq.fill(1000)(random.nextInt(2 * limit) - limit)

        for (input <- boundaries ++ nearMultiples ++ randomInputs) {
          dut.io.in.poke(input.S(width.W))
          dut.io.out.expect((input / 127).S(width.W))
        }
      }
    }
  }
}