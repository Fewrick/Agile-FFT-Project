
import help._

import chisel3._

class ApbPort extends Bundle {
  val psel = Input(Bool())
  val penable = Input(Bool())
  val pwrite = Input(Bool())
  val paddr = Input(UInt(32.W))
  val pwdata = Input(UInt(32.W))
  val prdata = Output(UInt(32.W))
  val pready = Output(Bool())
  val pslverr = Output(Bool())
}

class CsrAdapter(descriptionSheetPath: String) extends Module {

  private val blocks = CsrDescription.load(descriptionSheetPath)

  val apb = IO(new ApbPort)
  
  private def fieldPort(field: CsrDescription.Field): Data = field.typ match {
    case "rw" => Output(UInt(field.width.W))
    case "ro" => Input(UInt(field.width.W))
    case "wotrg" => new DynamicBundle(Seq(
      "data" -> Output(UInt(field.width.W)), "trg" -> Output(Bool())))
    case "rotrg" => new DynamicBundle(Seq(
      "data" -> Input(UInt(field.width.W)), "trg" -> Output(Bool())))
    case other => throw new IllegalArgumentException(s"No port for CSR type '$other'")
  }

  val csr = IO(new DynamicBundle(blocks.flatMap { block =>
    val ports = block.registers.flatMap { reg =>
      val fields = reg.fields.filter(_.typ != "const")
      if (fields.isEmpty) None
      else if (fields.head.name.isEmpty) Some(reg.name -> fieldPort(fields.head))
      else Some(reg.name -> new DynamicBundle(fields.map(f => f.name -> fieldPort(f))))
    }
    if (ports.isEmpty) None else Some(block.name -> new DynamicBundle(ports))
  }))

  // Every selected transfer completes in its first access cycle. No APB state
  // is needed: the master holds address, direction and data through this cycle.
  private val access = apb.psel && apb.penable && !reset.asBool
  apb.pready := access
  apb.pslverr := access // overridden only for a permitted address/direction
  apb.prdata := 0.U

  for (block <- blocks; reg <- block.registers) {
    val selected = apb.paddr === reg.address.U(32.W)
    val write = access && apb.pwrite && selected
    val read = access && !apb.pwrite && selected

    val readParts = reg.fields.flatMap { field =>
      val port = if (field.typ == "const") None else {
        val registerPort = csr(block.name).asInstanceOf[DynamicBundle](reg.name)
        Some(if (field.name.isEmpty) registerPort
          else registerPort.asInstanceOf[DynamicBundle](field.name))
      }
      val value: UInt = field.typ match {
        case "rw" | "wotrg" =>
          // '?' deliberately has no reset value, as in the reference generator.
          val stored = field.init match {
            case Some(init) => RegInit(init.U(field.width.W))
            case None => Reg(UInt(field.width.W))
          }
          when(write) { stored := apb.pwdata(field.high, field.low) }
          if (field.typ == "rw") port.get.asInstanceOf[UInt] := stored
          else {
            val trigger = port.get.asInstanceOf[DynamicBundle]
            trigger("data").asInstanceOf[UInt] := stored
            trigger("trg").asInstanceOf[Bool] := write
          }
          stored
        case "ro" => port.get.asInstanceOf[UInt]
        case "rotrg" =>
          val trigger = port.get.asInstanceOf[DynamicBundle]
          trigger("trg").asInstanceOf[Bool] := read
          trigger("data").asInstanceOf[UInt]
        case "const" => field.init.get.U(field.width.W)
      }
      if (field.readable) Some(value.pad(32) << field.low) else None
    }

    if (readParts.nonEmpty) {
      when(read) {
        apb.prdata := readParts.reduce(_ | _)
        apb.pslverr := false.B
      }
    }
    if (reg.fields.exists(_.writable)) {
      when(write) { apb.pslverr := false.B }
    }
  }

}

object CsrAdapter extends App {
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new CsrAdapter("soc.xlsx"),
    Array("--target-dir", "generated")
  )
}
