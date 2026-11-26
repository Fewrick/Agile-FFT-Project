package top
import chisel3.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import audioIO.Speaker
import timing.SampleRateTick

class AudioConfigurationTest extends AnyFlatSpec {
  it should "reject incompatible acquisition and recording configurations" in {
    for ((n, w, rate, length, clock) <- Seq(
      (4, 8, 12500, 9, 100000000),
      (4, 16, 12500, 8, 100000000),
      (4, 8, 10000, 8, 100000000),
      (4, 8, 12500, 8, 50000000),
      (3, 8, 12500, 9, 100000000))) {
      intercept[IllegalArgumentException] {
        (new ChiselStage).emitChirrtl(new AudioTop(n, w, rate, length, clock, bands = 16))
      }
    }
  }
  it should "reject playback without sufficient prefetch time or exact sample periods" in {
    intercept[IllegalArgumentException] {
      (new ChiselStage).emitChirrtl(new Speaker(8, 4, 1, 1))
    }
    intercept[IllegalArgumentException] {
      (new ChiselStage).emitChirrtl(new SampleRateTick(3, 10))
    }
  }
}
