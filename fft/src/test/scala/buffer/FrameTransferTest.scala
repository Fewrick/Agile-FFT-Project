package buffer

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FrameTransferTest extends AnyFlatSpec with ChiselScalatestTester {
  for (loading <- Seq(true, false)) {
    it should s"transfer exact synchronous responses repeatedly (loading=$loading)" in {
      // Both controllers use the same port shapes, with reversed address widths.
      if (loading) test(new FrameLoader(8, 4, 12)) { d =>
        d.io.start.poke(false.B); d.io.readData.poke(0.S)
        for (base <- Seq(0, 8, 4, 0)) {
          d.io.frameStart.poke(base.U); d.io.start.poke(true.B); d.clock.step()
          d.io.start.poke(false.B)
          var reads = 0; var writes = 0; var response = 0; var cycles = 0
          while (!d.io.done.peek().litToBoolean && cycles < 20) {
            d.io.busy.expect(true.B)
            d.io.readData.poke(response.S)
            if (d.io.writeEn.peek().litToBoolean) {
              d.io.writeAddr.expect(writes.U); d.io.writeData.expect((base + writes + 17).S)
              writes += 1
            }
            if (d.io.readEn.peek().litToBoolean) {
              d.io.readAddr.expect((base + reads).U); response = base + reads + 17; reads += 1
            }
            d.clock.step(); cycles += 1
          }
          assert(reads == 4 && writes == 4); d.io.done.expect(true.B)
          d.io.busy.expect(false.B); d.io.writeEn.expect(false.B)
          d.clock.step(); d.io.done.expect(false.B)
        }
      } else test(new FrameStore(8, 4, 12)) { d =>
        d.io.start.poke(false.B); d.io.readData.poke(0.S)
        for (base <- Seq(0, 8, 4, 0)) {
          d.io.frameStart.poke(base.U); d.io.start.poke(true.B); d.clock.step()
          d.io.start.poke(false.B)
          var reads = 0; var writes = 0; var response = 0; var cycles = 0
          while (!d.io.done.peek().litToBoolean && cycles < 20) {
            d.io.busy.expect(true.B); d.io.readData.poke(response.S)
            if (d.io.writeEn.peek().litToBoolean) {
              d.io.writeAddr.expect((base + writes).U); d.io.writeData.expect((writes - 17).S); writes += 1
            }
            if (d.io.readEn.peek().litToBoolean) {
              d.io.readAddr.expect(reads.U); response = reads - 17; reads += 1
            }
            d.clock.step(); cycles += 1
          }
          assert(reads == 4 && writes == 4); d.io.done.expect(true.B)
          d.io.busy.expect(false.B); d.io.writeEn.expect(false.B)
          d.clock.step(); d.io.done.expect(false.B)
        }
      }
    }
  }
}
