package timing
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SampleRateTickTest extends AnyFlatSpec with ChiselScalatestTester {
  for (period <- Seq(1, 2, 7, 8000)) {
    it should s"tick exactly every $period clocks and restart its phase" in {
      test(new SampleRateTick(1, period)) { d =>
        d.clock.setTimeout(0)
        for (_ <- 0 until 2) {
          d.io.enable.poke(false.B); d.clock.step(3); d.io.tick.expect(false.B)
          d.io.enable.poke(true.B)
          for (cycle <- 1 to period * 3) {
            d.io.tick.expect((cycle % period == 0).B); d.clock.step()
          }
          d.clock.step(math.max(0, period / 2))
        }
      }
    }
  }
}
