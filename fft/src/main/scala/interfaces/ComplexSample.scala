package interfaces

import chisel3._

class ComplexSample(width: Int) extends Bundle {
    val re = SInt(width.W)
    val im = SInt(width.W)
}