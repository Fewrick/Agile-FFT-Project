package audioIO

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec


class MicrophoneTest extends AnyFlatSpec with ChiselScalatestTester {
    private val dataWidth = 8
    private val pdmWordWidth = 16
    private val decimation = 10
    private val expectedFirstWriteRisingEdge = pdmWordWidth * decimation

    private val testCycles = 10000

    "Microphone" should "stop writing when disabled while keeping micClock running" in {
    test(new Microphone(dataWidth))
        .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        dut.io.micEn.poke(true.B)
        dut.io.micDataStream.poke(true.B)
        var initialWrite = false
        var cycles = 0
        while (cycles < testCycles && !initialWrite) {
            dut.clock.step()
            initialWrite = dut.io.writeEn.peek().litToBoolean
            cycles += 1
        }
        assert(initialWrite, "no initial PCM sample was produced")

        dut.io.micEn.poke(false.B)
        dut.clock.step()
        dut.io.writeEn.expect(false.B)

        var micClockLow = false
        var micClockHigh = false
        for (i <- 0 until 200) {
            dut.io.micDataStream.poke((i % 2 == 0).B)
            dut.clock.step()
            dut.io.writeEn.expect(false.B)
            if (dut.io.micClock.peek().litToBoolean) micClockHigh = true
            else micClockLow = true
        }

        assert(micClockHigh && micClockLow, "micClock did not toggle while capture was disabled")
        }
    }

    "Microphone" should "capture and process input when enable is high" in {
    test(new Microphone(dataWidth))
        .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        dut.io.micEn.poke(false.B)
        dut.io.micDataStream.poke(false.B)
        dut.clock.step(2)
        dut.io.micEn.poke(true.B)
        dut.io.micDataStream.poke(true.B)

        var previousMicClock = dut.io.micClock.peek().litToBoolean
        var micClockRisingEdges = 0
        var firstWriteRisingEdge = -1
        var firstWriteData = 0
        for (_ <- 0 until testCycles) {
            dut.clock.step()
            val micClock = dut.io.micClock.peek().litToBoolean
            if (!previousMicClock && micClock) micClockRisingEdges += 1
            previousMicClock = micClock

            if (dut.io.writeEn.peek().litToBoolean) {
                if (firstWriteRisingEdge < 0) {
                    firstWriteRisingEdge = micClockRisingEdges
                    firstWriteData = dut.io.writeData.peek().litValue.toInt
                }
            }
        }
        println(f"First data output = $firstWriteData%d")

        assert(
          firstWriteRisingEdge == expectedFirstWriteRisingEdge,
          s"expected the first PCM sample after $expectedFirstWriteRisingEdge microphone-clock edges, got $firstWriteRisingEdge"
        )
        assert(firstWriteData == 127, s"expected all-one PDM input to produce 127, got $firstWriteData")
        }
    }

    // "Microphone" should "not capture any input when enable is low" in {
    //     test(new Microphone(8)).withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
    //         dut.io.micEn.poke(false.B)
    //         for (_ <- 0 until decimation/2) {
    //             dut.io.micDataStream.poke(false.B)
    //             dut.clock.step()
    //             dut.io.micDataStream.poke(true.B)
    //             dut.clock.step()
    //         }
    //         val enable = dut.io.writeEn.peek().litToBoolean
    //         val dout = dut.io.writeData.peek().litValue

    //         println(f"Output value = $dout%d \t Valid = $enable")
            
    //         dut.io.writeData.expect(0.S)
    //     }
    // }
    // "Microphone" should "capture and process input when enable is high" in {
    //     test(new Microphone(8)).withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
    //         dut.io.micEn.poke(true.B)
    //         val i = 0
    //         for (i <- 0 until decimation) {
    //             if(i%2 == 0){
    //                 dut.io.micDataStream.poke(false.B)
    //             } else {
    //                 dut.io.micDataStream.poke(true.B)
    //             }
    //             dut.clock.step()
    //             val enable = dut.io.writeEn.peek().litToBoolean
    //             val dout = dut.io.writeData.peek().litValue
    //             println(f"Step = $i%d \tOutput value = $dout%d \t Valid = $enable")
    //         }
    //         val j = 0
    //         for (j <- 0 until 100) {
    //             val enable = dut.io.writeEn.peek().litToBoolean
    //             if (enable) {
    //                 val dout = dut.io.writeData.peek().litValue
    //                 println(f"Output value = $dout%d \t Valid = $enable")
    //             }
    //             dut.clock.step()
    //         }            
    //         // dut.io.writeData.expect(0.U)
    //         // dut.io.writeEn.expect(true)
    //     }
    // }
    // "Microphone" should "capture and convert to integers when enable is high" in {
    //     test(new MicTop(8)) { dut =>

    //     }
    // }

}