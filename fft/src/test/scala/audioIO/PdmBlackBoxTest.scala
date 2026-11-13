package audioio

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class PdmDesHarness extends Module {
  val io = IO(new Bundle {
    val en          = Input(Bool())
    val pdm_m_data_i = Input(Bool())
    val done        = Output(Bool())
    val dout        = Output(UInt(16.W))
    val pdm_m_clk_o = Output(Bool())
  })

  val pdm = Module(new PdmDes)
  pdm.io.clk          := clock
  pdm.io.en           := io.en
  pdm.io.pdm_m_data_i := io.pdm_m_data_i
  io.done             := pdm.io.done
  io.dout             := pdm.io.dout
  io.pdm_m_clk_o      := pdm.io.pdm_m_clk_o
}

class PDMBlackBoxTest extends AnyFlatSpec with ChiselScalatestTester {
  "PDM blackbox" should "print its outputs" in {
    test(new PdmDesHarness)
      .withAnnotations(Seq(VerilatorBackendAnnotation)) { dut =>
        dut.io.en.poke(false.B)
        dut.io.pdm_m_data_i.poke(false.B)
        dut.clock.step(2)

        dut.io.en.poke(true.B)

        for (cycle <- 0 until 1000) {
          // A simple repeating input pattern; this test is for observing outputs,
          // not checking decoded audio values.
          dut.io.pdm_m_data_i.poke(((cycle / 50) % 2 == 0).B)
          dut.clock.step()

          val done = dut.io.done.peek().litToBoolean
          if (cycle % 25 == 0 || done) {
            val dout = dut.io.dout.peek().litValue
            println(
              f"cycle=$cycle%4d pdm_clk=${dut.io.pdm_m_clk_o.peek().litToBoolean} " +
              f"data=${dut.io.pdm_m_data_i.peek().litToBoolean} done=$done dout=0x$dout%04X"
            )
          }
        }
      }
  }
}