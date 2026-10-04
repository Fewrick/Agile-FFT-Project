import help.Sheet

/** Elaboration-time spreadsheet parsing. */
object CsrDescription {
  case class Field(name: String, typ: String, high: Int, low: Int, init: Option[BigInt]) {
    def width: Int = high - low + 1
    def readable: Boolean = typ != "wotrg"
    def writable: Boolean = typ == "rw" || typ == "wotrg"
  }
  case class Register(name: String, address: BigInt, fields: Seq[Field])
  case class Block(name: String, registers: Seq[Register])

  // POI represents numeric Excel cells as e.g. "0.0". Keep addresses unsigned
  // and exact, including the upper half of the 32-bit address space.
  private def number(text: String): BigInt = {
    val value = text.trim
    if (value.toLowerCase.startsWith("0x")) BigInt(value.drop(2), 16)
    else BigDecimal(value).toBigIntExact.getOrElse(
      throw new IllegalArgumentException(s"Expected an integer, got '$text'"))
  }

  private def records(sheet: Sheet): Seq[Map[String, String]] =
    sheet.rows.filter(_.head.trim.nonEmpty).map(row =>
      sheet.header.zip(row.map(_.trim)).toMap)

  def load(path: String): Seq[Block] = {
    val sheets = Sheet.load(path)
    val blocks = records(sheets("Map")).map { entry =>
      val base = number(entry("Base Address"))
      val end = number(entry("End Address"))
      require(base >= 0 && base <= end && end < (BigInt(1) << 32),
        s"Invalid address range for ${entry("Name")}")
      val rows = records(sheets(entry("Block")))
      val registers = rows.map(_("Register")).distinct.map { name =>
        val registerRows = rows.filter(_("Register") == name)
        val offsets = registerRows.map(r => number(r("Offset"))).distinct
        require(offsets.size == 1, s"Inconsistent offsets for $name")
        val address = base + offsets.head
        require(offsets.head >= 0 && address + 3 <= end && address % 4 == 0,
          s"Register $name is unaligned or outside its block")
        val fields = registerRows.map { row =>
          val typ = row("Type")
          require(Set("rw", "ro", "wotrg", "rotrg", "const")(typ), s"Unknown CSR type '$typ'")
          val range = row("Range").split(":").map(_.trim.toInt)
          require(range.length == 2 && range(1) >= 0 && range(0) < 32 && range(0) >= range(1),
            s"Invalid range for $name: ${row("Range")}")
          val init = if (row("Init") == "?" || row("Init").isEmpty) None
            else Some(number(row("Init")))
          val field = Field(row("Field"), typ, range(0), range(1), init)
          require(typ != "const" || init.nonEmpty, s"Constant $name needs an initial value")
          require(init.forall(v => v >= 0 && v < (BigInt(1) << field.width)),
            s"Initial value does not fit $name.${field.name}")
          field
        }
        require(fields.size == 1 || fields.forall(_.name.nonEmpty),
          s"Cannot mix whole-register and named fields in $name")
        require(fields.map(_.name).distinct.size == fields.size, s"Duplicate fields in $name")
        // Read and write fields may share bits (e.g. UART RX/TX), but two
        // readable fields or two writable fields must not drive the same bits.
        for (pair <- fields.combinations(2) if pair.size == 2) {
          val Seq(a, b) = pair
          val overlap = a.low <= b.high && b.low <= a.high
          require(!overlap || !(a.readable && b.readable || a.writable && b.writable),
            s"Overlapping fields in $name")
        }
        Register(name, address, fields)
      }
      Block(entry("Name"), registers)
    }
    require(blocks.map(_.name).distinct.size == blocks.size, "Duplicate block instance names")
    val addresses = blocks.flatMap(_.registers.map(_.address))
    require(addresses.distinct.size == addresses.size, "Duplicate register addresses")
    blocks
  }
}
