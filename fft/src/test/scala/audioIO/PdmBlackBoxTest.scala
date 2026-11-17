package audioIO

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class PdmDesWrapper(
    systemClockFreqHz: Int = 100000000,
    pdmClockFreqHz: Int = 2000000
) extends Module {
  val io = IO(new Bundle {
    val en          = Input(Bool())
    val pdm_m_data_i = Input(Bool())
    val done        = Output(Bool())
    val dout        = Output(UInt(16.W))
    val pdm_m_clk_o = Output(Bool())
  })

  val pdm = Module(new PdmDes(systemClockFreqHz, pdmClockFreqHz))
  pdm.io.clk          := clock
  pdm.io.en           := io.en
  pdm.io.pdm_m_data_i := io.pdm_m_data_i
  io.done             := pdm.io.done
  io.dout             := pdm.io.dout
  io.pdm_m_clk_o      := pdm.io.pdm_m_clk_o
}

class PDMBlackBoxTest extends AnyFlatSpec with ChiselScalatestTester {
  // "PDM blackbox" should "print its outputs" in {
  //   test(new PdmDesWrapper)
  //   .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
  //     dut.io.en.poke(false.B)
  //     dut.io.pdm_m_data_i.poke(false.B)
  //     dut.clock.step(2)

  //     dut.io.en.poke(true.B)

  //     for (cycle <- 0 until 1000) {
  //       // A simple repeating input pattern; this test is for observing outputs,
  //       // not checking decoded audio values.
  //       dut.io.pdm_m_data_i.poke(((cycle / 50) % 2 == 0).B)
  //       dut.clock.step()

  //       val done = dut.io.done.peek().litToBoolean
  //       if (cycle % 25 == 0 || done) {
  //         val dout = dut.io.dout.peek().litValue
  //         println(
  //           f"cycle=$cycle%4d pdm_clk=${dut.io.pdm_m_clk_o.peek().litToBoolean} " +
  //           f"data=${dut.io.pdm_m_data_i.peek().litToBoolean} done=$done dout=0x$dout%04X"
  //         )
  //       }
  //     }
  //   }
  // }

  "PDM blackbox" should "deserialize 16 serial bits into dout" in {
    test(new PdmDesWrapper)
    .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
      dut.io.en.poke(false.B)
      dut.io.pdm_m_data_i.poke(false.B)
      dut.clock.step(2)
      dut.io.en.poke(true.B)

      def sampleOnNextPdmRise(bit: Boolean, expectedDone: Boolean): Unit = {
        while (dut.io.pdm_m_clk_o.peek().litToBoolean) {
            dut.clock.step()
        }
        while (!dut.io.pdm_m_clk_o.peek().litToBoolean) {
            dut.clock.step()
        }

        dut.io.pdm_m_data_i.poke(bit.B)
        dut.clock.step()

        // For debugging
        val pdm_clk = dut.io.pdm_m_clk_o.peek().litToBoolean
        val done = dut.io.done.peek().litToBoolean
        val dout = dut.io.dout.peek().litValue
        val inputValue = if (bit) 1 else 0

        println(f"input=$inputValue%d \tpdm_clk=$pdm_clk \tdone=$done \tdout=0x$dout%04X")


        dut.io.done.expect(expectedDone.B)
      }

      val pattern = 0xA35C
      val bits = (15 to 0 by -1).map(bit => ((pattern >> bit) & 1) != 0)

      bits.zipWithIndex.foreach { case (bit, index) =>
        sampleOnNextPdmRise(bit, expectedDone = index == bits.length - 1)
      }

      dut.io.dout.expect(pattern.U(16.W))
    }
  }

  it should "derive the PDM clock divider from the configured system clock" in {
    test(new PdmDesWrapper(systemClockFreqHz = 100000000, pdmClockFreqHz = 1000000))
      .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.io.en.poke(false.B)
        dut.io.pdm_m_data_i.poke(false.B)

        var level = dut.io.pdm_m_clk_o.peek().litToBoolean
        var firstTransitionCycles = 0
        while (dut.io.pdm_m_clk_o.peek().litToBoolean == level && firstTransitionCycles < 51) {
          dut.clock.step()
          firstTransitionCycles += 1
        }
        assert(firstTransitionCycles <= 50, "PDM clock did not transition within its configured half-period")

        level = dut.io.pdm_m_clk_o.peek().litToBoolean
        var cycles = 0
        while (dut.io.pdm_m_clk_o.peek().litToBoolean == level && cycles < 51) {
          dut.clock.step()
          cycles += 1
        }

        assert(cycles == 50, s"expected 50 system-clock cycles per PDM half-period, got $cycles")
      }
  }
}