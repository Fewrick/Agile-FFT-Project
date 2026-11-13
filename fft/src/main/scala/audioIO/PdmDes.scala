package audioio

import chisel3._
import chisel3.util.HasBlackBoxResource

class PdmDes extends BlackBox
    with HasBlackBoxResource {

  override def desiredName: String = "PdmDes"

  val io = IO(new Bundle {
    val clk          = Input(Clock())
    val en           = Input(Bool())
    val done         = Output(Bool())
    val dout         = Output(UInt(16.W))
    val pdm_m_clk_o  = Output(Bool())
    val pdm_m_data_i = Input(Bool())
  })

  addResource("/PdmDes.v")
}