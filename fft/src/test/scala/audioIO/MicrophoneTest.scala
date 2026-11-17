package audioIO

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec


class MicrophoneTest extends AnyFlatSpec with ChiselScalatestTester {
    private val dataWidth = 8

    private val testCycles = 20000

    "Microphone" should "not capture any input when enable is low" in {
    test(new Microphone(dataWidth))
        .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        dut.io.micEn.poke(false.B)

        var writeDetected = false
        for (cycle <- 0 until testCycles) {
            // Keep the input active to check that micEn gates it.
            dut.io.micDataStream.poke((cycle % 2 == 0).B)
            dut.clock.step()
            writeDetected = writeDetected || dut.io.writeEn.peek().litToBoolean
        }

        assert(!writeDetected, "writeEn became true while micEn was low")
        }
    }

    "Microphone" should "capture and process input (all ones) when enable is high" in {
    test(new Microphone(dataWidth))
        .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.clock.setTimeout(0)

        dut.io.micEn.poke(true.B)
        dut.io.micDataStream.poke(true.B)

        var writeDetected = false
        var nonZeroWriteDetected = false
        // var dout = 0
        for (i <- 0 until testCycles) {
            dut.clock.step()
            val dout = dut.io.writeData.peek().litValue
            val dvalid = dut.io.writeEn.peek().litToBoolean
            // if (((i>=8020) && (i<=8030)) || ((i>=16820) && (i<=16830))) {
            //     println(f"Step=$i%d \t dout=$dout%d \t writeEn=$dvalid")
            // }
            if (dvalid) {
                writeDetected = true
                
                println(f"writeEn went high at step=$i%d \t")
                if (dout != 0) {
                    nonZeroWriteDetected = true
                    println(f"... with value $dout%d")
                    println("")
                    assert(dout==127, "output not 127 when input is all ones")
                }
            }
        }

        assert(writeDetected, "no valid PCM sample was produced")
        assert(nonZeroWriteDetected, "no nonzero PCM sample was produced")
        }
    }
}