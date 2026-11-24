package audioIO
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PlaybackTest extends AnyFlatSpec with ChiselScalatestTester {
  for (period <- Seq(2, 7, 8000); data <- Seq(Seq(-128, -1, 0, 127), Seq(37))) {
    it should s"play ${data.size} samples for exactly $period clocks each, twice" in {
      test(new Speaker(8, data.size, 1, period)) { d =>
        d.clock.setTimeout(0)
        d.io.start.poke(false.B); d.io.readData.poke(0.S)
        for (_ <- 0 until 2) {
          d.io.sampleOut.expect(128.U)
          d.io.start.poke(true.B); d.clock.step(); d.io.start.poke(false.B)
          var response = 0; var reads = 0; var cycle = 0
          var first = -1
          while (!d.io.done.peek().litToBoolean && cycle < period * data.size + 10) {
            d.io.readData.poke(response.S)
            if (d.io.readEn.peek().litToBoolean) {
              d.io.readAddr.expect(reads.U); response = data(reads); reads += 1
            }
            val actual = d.io.sampleOut.peek().litValue.toInt
            if (first < 0 && actual == (data.head + 128)) first = cycle
            if (first >= 0) {
              val index = (cycle - first) / period
              assert(index < data.size)
              d.io.sampleOut.expect((data(index) + 128).U)
            }
            d.clock.step(); cycle += 1
          }
          d.io.done.expect(true.B); d.io.busy.expect(false.B)
          assert(reads == data.size && cycle - first == data.size * period)
          d.io.sampleOut.expect(128.U); d.clock.step(2)
        }
      }
    }
  }
}
