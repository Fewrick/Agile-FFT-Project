package audioIO

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec


class MicrophoneTest extends AnyFlatSpec with ChiselScalatestTester {
    private val dataWidth = 8
    private val decimation = 10

    private val testCycles = 10000

    "Microphone" should "not capture any input when enable is low" in {
    test(new Microphone(8, 32))
        .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        dut.io.micEn.poke(false.B)

        var sawWrite = false
        for (cycle <- 0 until testCycles) {
            // Keep the input active to check that micEn gates it.
            dut.io.micDataStream.poke((cycle % 2 == 0).B)
            dut.clock.step()
            sawWrite = sawWrite || dut.io.writeEn.peek().litToBoolean
        }

        assert(!sawWrite, "writeEn became true while micEn was low")
        }
    }

    "Microphone" should "capture and process input when enable is high" in {
    test(new Microphone(8, 32))
        .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        dut.io.micEn.poke(true.B)
        dut.io.micDataStream.poke(true.B)

        var sawWrite = false
        var sawNonZeroWrite = false
        for (i <- 0 until testCycles) {
            dut.clock.step()
            if (dut.io.writeEn.peek().litToBoolean) {
                sawWrite = true
                
                println(f"Step = $i%d \t")
                if (dut.io.writeData.peek().litValue != 0) {
                    sawNonZeroWrite = true
                }
            }
        }

        assert(sawWrite, "no valid PCM sample was produced")
        assert(sawNonZeroWrite, "no nonzero PCM sample was produced")
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