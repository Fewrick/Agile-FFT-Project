import help._

import chisel3._
import chisel3.util._

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

// ---------------------------------------------------------------------------
// CSR description as Scala data (case classes, see lecture 03/04)
// ---------------------------------------------------------------------------
sealed trait FieldType
case object Rw extends FieldType
case object Ro extends FieldType
case object WoTrg extends FieldType
case object RoTrg extends FieldType
case object Const extends FieldType

case class Field(name: String, typ: FieldType, hi: Int, lo: Int, init: Option[BigInt]) {
  require(hi >= lo && hi < 32, s"Invalid range $hi:$lo for field '$name'")
  def width = hi - lo + 1
}
case class Register(name: String, offset: BigInt, fields: Seq[Field]) {
  def plain = fields.size == 1 && fields.head.name.isEmpty // a register without sub-fields is represented by one field with an empty name
}
case class BlockInst(name: String, base: BigInt, regs: Seq[Register])
case class FieldPort(addr: BigInt, field: Field, port: Option[Data]) // one field of one block instance, with its absolute address and its port in csr

// Scala object to parse the CSR description from Excel sheets into the data structures
object CsrSpec {
  def toNum(s: String): BigInt = {
    val t = s.trim.toLowerCase
    if (t.startsWith("0x")) BigInt(t.drop(2), 16) else BigDecimal(t).toBigInt
  }

  def toType(s: String): FieldType = s.trim match {
    case "rw"    => Rw
    case "ro"    => Ro
    case "wotrg" => WoTrg
    case "rotrg" => RoTrg
    case "const" => Const
    case other   => throw new IllegalArgumentException(s"Unknown CSR type '$other'")
  }

  def registers(sheet: Sheet): Seq[Register] = {
    val h = sheet.header
    val (iReg, iOff, iFld) = (h.indexOf("Register"), h.indexOf("Offset"), h.indexOf("Field"))
    val (iTyp, iRng, iIni) = (h.indexOf("Type"), h.indexOf("Range"), h.indexOf("Init"))

    sheet.column("Register").distinct.map { regName =>
      val rows = sheet.filterRows(row => row(iReg) == regName)
      val fields = rows.map { row =>
        val Array(hi, lo) = row(iRng).trim.split(":").map(_.trim.toInt)
        val init = row(iIni).trim
        Field(
          row(iFld).trim,
          toType(row(iTyp)),
          hi,
          lo,
          if (init == "?" || init.isEmpty) None else Some(toNum(init))
        )
      }
      Register(regName, toNum(rows.head(iOff)), fields)
    }
  }

  def parse(map: Sheet, sheets: Map[String, Sheet]): Seq[BlockInst] = {
    val types = map.column("Block")
    val names = map.column("Name")
    val bases = map.column("Base Address")
    Seq.tabulate(types.length) { i =>
      BlockInst(names(i), toNum(bases(i)), registers(sheets(types(i)))) // one BlockInst per row of the Map sheet
    }
  }
}

// ----- The generator -----
class CsrAdapter(descriptionSheetPath: String) extends Module {

  val sheets = Sheet.load(descriptionSheetPath)
  val map = sheets("Map")
  println(map)

  val blocks = CsrSpec.parse(map, sheets)

  // ----- IO: csr.<instance>.<reg>[.<field>][.data | .trg] -----
  def fieldIo(f: Field): Option[Data] = f.typ match {
    case Rw    => Some(Output(UInt(f.width.W)))
    case Ro    => Some(Input(UInt(f.width.W)))
    case WoTrg => Some(new DynamicBundle(Seq("data" -> Output(UInt(f.width.W)), "trg" -> Output(Bool()))))
    case RoTrg => Some(new DynamicBundle(Seq("data" -> Input(UInt(f.width.W)), "trg" -> Output(Bool()))))
    case Const => None // hardwired inside the adapter
  }

  def regIo(r: Register): Option[Data] = {
    if (r.plain) {
      fieldIo(r.fields.head)
    } else {
      val fields = r.fields.flatMap(f => fieldIo(f).map(f.name -> _))
      if (fields.isEmpty) None else Some(new DynamicBundle(fields))
    }
  }

  def blockIo(b: BlockInst): Option[Data] = {
    val regs = b.regs.flatMap(r => regIo(r).map(r.name -> _))
    if (regs.isEmpty) None else Some(new DynamicBundle(regs))
  }

  val apb = IO(new ApbPort)
  val csr = IO(new DynamicBundle(blocks.flatMap(b => blockIo(b).map(b.name -> _))))

  // ----- access to the generated ports -----
  def sub(d: Data, name: String): Data = d.asInstanceOf[DynamicBundle](name)

  def portOf(b: BlockInst, r: Register, f: Field): Data = {
    val reg = sub(csr(b.name), r.name)
    if (r.plain) reg else sub(reg, f.name)
  }

  // every field with absolute address and port (None for const)
  val entries = for {
    b <- blocks
    r <- b.regs
    f <- r.fields
  } yield FieldPort(b.base + r.offset, f, if (f.typ == Const) None else Some(portOf(b, r, f)))

  // ----- APB handshake -----
  val wrAccess = RegInit(false.B)
  val rdAccess = RegInit(false.B)

  wrAccess := !wrAccess && apb.psel && apb.pwrite
  rdAccess := !rdAccess && apb.psel && !apb.pwrite
  apb.pready := wrAccess || rdAccess

  def hit(addr: BigInt): Bool = apb.paddr === addr.U(32.W)

  // ----- hardware of one field -----
  // Generates registers, outputs and triggers. Returns what the field contributes to prdata (already shifted to its bit position), if readable.
  def field(e: FieldPort): Option[UInt] = {
    val f = e.field
    e.field.typ match {
      case Rw | WoTrg =>
        val reg = f.init match {
          case Some(v) => RegInit(v.U(f.width.W))
          case None    => Reg(UInt(f.width.W))
        }
        when(wrAccess && hit(e.addr)) {
          reg := apb.pwdata(f.hi, f.lo)
        }
        if (f.typ == Rw) {
          e.port.get := reg
          Some(reg << f.lo)
        } else {
          sub(e.port.get, "data") := reg
          sub(e.port.get, "trg") := wrAccess && hit(e.addr)
          None
        }
      case Ro =>
        Some(e.port.get.asUInt << f.lo)
      case RoTrg =>
        sub(e.port.get, "trg") := rdAccess && hit(e.addr)
        Some(sub(e.port.get, "data").asUInt << f.lo)
      case Const =>
        require(f.init.isDefined, s"const field '${f.name}' needs an init value")
        Some(f.init.get.U(f.width.W) << f.lo)
    }
  }

  // the tuple (address, value) for all readable fields
  val reads = for {
    e <- entries
    v <- field(e)
  } yield (e.addr, v)

  // all fields of the same register are OR'ed into one read word
  val readWords = reads.groupBy(_._1).toSeq.map { case (addr, vs) =>
    (addr, vs.map(_._2).reduce(_ | _))
  }

    // ----- read access -----
  apb.prdata := 0.U
  when(apb.psel) {
    for ((addr, word) <- readWords) {
      when(hit(addr)) {
        apb.prdata := word
      }
    }
  }

  // ----- APB error handling -----
  def inSet(addrs: Seq[BigInt]): Bool = addrs.foldLeft(false.B)((acc, a) => acc || hit(a))

  val readable = inSet(entries.filter(_.field.typ != WoTrg).map(_.addr).distinct)
  val writable = inSet(entries.filter(e => e.field.typ == Rw || e.field.typ == WoTrg).map(_.addr).distinct)

  apb.pslverr := false.B
  when(apb.psel) {
    when(rdAccess) {
      apb.pslverr := !readable
    }.elsewhen(wrAccess) {
      apb.pslverr := !writable
    }
  }
}

object CsrAdapter extends App {
  emitVerilog(
    new CsrAdapter("soc.xlsx"),
    Array("--target-dir", "generated")
  )
}