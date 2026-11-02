package interfaces

import chisel3._

class DSPIO[T <: Data](gen: T) extends Bundle {
    val data = Output(gen)
    val valid = Output(Bool())
    val ready = Input(Bool())
}