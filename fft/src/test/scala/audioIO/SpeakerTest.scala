package audioIO

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SpeakerTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("Nexys A7 Speaker Path")

  "Speaker Test" should "assert audSd and vary PWM duty cycle with sample values" in {
    test(new PWMSpeaker(width = 8)) { s =>
      assert(s.io.audioEn.peek().litToBoolean == true)

      def measureHighCycles(sampleVal: Int): Int = {
        s.io.sampleIn.poke(sampleVal.U)
        s.clock.step(1)
        var highCount = 0
        for (_ <- 0 until 256) {
          if (s.io.pwmOut.peek().litToBoolean) highCount += 1
          s.clock.step(1)
        }
        highCount
      }

      val lowHigh  = measureHighCycles(32)
      val midHigh  = measureHighCycles(128)
      val highHigh = measureHighCycles(220)

      assert(lowHigh > midHigh, s"Expected low sample ($lowHigh) > mid sample ($midHigh)")
      assert(midHigh > highHigh, s"Expected mid sample ($midHigh) > high sample ($highHigh)")
    }
  }
}