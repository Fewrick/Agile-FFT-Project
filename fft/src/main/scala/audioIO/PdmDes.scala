package audioIO

import chisel3._
import chisel3.experimental.IntParam
import chisel3.util.HasBlackBoxResource

class PdmDes(
    systemClockFreqHz: Int = 100000000,
    pdmClockFreqHz: Int = 2000000
) extends BlackBox(Map(
      "C_SYS_CLK_FREQ_HZ" -> IntParam(systemClockFreqHz),
      "C_PDM_FREQ_HZ" -> IntParam(pdmClockFreqHz)
    ))
    with HasBlackBoxResource {

  require(systemClockFreqHz > 0)
  require(pdmClockFreqHz > 0)
  require(systemClockFreqHz.toLong % (2L * pdmClockFreqHz) == 0)

  override def desiredName: String = "PdmDes"

  val io = IO(new Bundle {
    val clk          = Input(Clock())
    val reset        = Input(Bool())
    val en           = Input(Bool())
    val done         = Output(Bool())
    val dout         = Output(UInt(16.W))
    val pdm_m_clk_o  = Output(Bool())
    val pdm_m_data_i = Input(Bool())
  })

  addResource("/PdmDes.v")
}