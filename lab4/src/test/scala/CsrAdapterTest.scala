import chisel3._
import chiseltest._
import help.DynamicBundle
import org.scalatest.flatspec.AnyFlatSpec
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files

class CsrAdapterTest extends AnyFlatSpec with ChiselScalatestTester {
  private def signal(dut: CsrAdapter, path: String): Data =
    path.split('.').foldLeft(dut.csr: Data) { (port, name) =>
      port.asInstanceOf[DynamicBundle](name)
    }
  private def uint(dut: CsrAdapter, path: String): UInt = signal(dut, path).asInstanceOf[UInt]
  private def bool(dut: CsrAdapter, path: String): Bool = signal(dut, path).asInstanceOf[Bool]
  private def master(dut: CsrAdapter): ApbMasterBfm = new ApbMasterBfm(
    dut.clock, dut.reset, dut.apb.psel, dut.apb.penable, dut.apb.paddr,
    dut.apb.pwrite, dut.apb.pwdata, dut.apb.prdata, dut.apb.pready, dut.apb.pslverr)

  "Chisel CSR adapter" should "implement reset, field masking, live inputs and independent instances" in {
    test(new CsrAdapter("soc.xlsx")) { dut =>
      val bfm = master(dut)
      bfm.reset()
      assert(!dut.csr.elements.contains("sysInfo"))
      bfm.readExpect(0x80000000L, Some(0xdeadbeefL))
      val writable = Seq(0x41003000L -> BigInt(3), 0x41004000L -> BigInt(1),
        0x41004004L -> BigInt("ffffffff", 16), 0x4100400cL -> BigInt("ffffffff", 16),
        0x41004010L -> BigInt(1), 0x41004014L -> BigInt("ffffffff", 16),
        0x4100401cL -> BigInt("ffffffff", 16))
      writable.foreach { case (addr, _) => bfm.readExpect(addr, Some(0)) }
      val random = new scala.util.Random(42)
      val expected = scala.collection.mutable.Map(writable.map { case (a, _) => a -> BigInt(0) }: _*)
      for (_ <- 0 until 12; (addr, mask) <- writable) {
        val value = BigInt(32, random)
        assert(bfm.write(addr, value).nonEmpty)
        expected(addr) = value & mask
        writable.foreach { case (a, _) => bfm.readExpect(a, Some(expected(a))) }
      }
      uint(dut, "gpio0.dir").expect(expected(0x41004004L).U)
      uint(dut, "gpio1.dataOut").expect(expected(0x4100401cL).U)
      uint(dut, "gpio0.dataIn").poke("hdeadbeef".U)
      uint(dut, "gpio1.dataIn").poke("h80000001".U)
      bfm.readExpect(0x41004008L, Some(0xdeadbeefL))
      bfm.readExpect(0x41004018L, Some(0x80000001L))
      for (status <- 0 until 4) {
        uint(dut, "uart0.status.txEmpty").poke((status & 1).U)
        uint(dut, "uart0.status.rxReady").poke((status >> 1).U)
        bfm.readExpect(0x41003004L, Some(status))
      }
      bfm.reset()
      writable.foreach { case (addr, _) => bfm.readExpect(addr, Some(0)) }
    }
  }

  it should "reject invalid addresses and writes to read-only or constant registers" in {
    test(new CsrAdapter("soc.xlsx")) { dut =>
      val bfm = master(dut)
      bfm.reset()
      for (addr <- Seq(0L, 0x50000000L, 0xffffffffL, 0x41003001L, 0x4100300cL, 0x41004020L)) {
        bfm.readExpect(addr, None)
        assert(bfm.write(addr, 0xffffffffL).isEmpty)
      }
      for (addr <- Seq(0x41003004L, 0x41004008L, 0x41004018L, 0x80000000L))
        assert(bfm.write(addr, 0xffffffffL).isEmpty)
      bfm.readExpect(0x41003000L, Some(0))
      bfm.readExpect(0x80000000L, Some(0xdeadbeefL))
      dut.apb.pslverr.expect(false.B)
    }
  }

  it should "gate writes and triggers to access cycles and support back-to-back transfers" in {
    test(new CsrAdapter("soc.xlsx")) { dut =>
      val bfm = master(dut)
      bfm.reset()
      val tx = bool(dut, "uart0.data.txData.trg")
      val rx = bool(dut, "uart0.data.rxData.trg")
      val data = uint(dut, "uart0.data.txData.data")
      uint(dut, "uart0.data.rxData.data").poke(0xa5.U)
      assert(bfm.write(0x41003008L, 0x12).nonEmpty)
      data.expect(0x12.U)
      var previous = 0x12
      for (value <- Seq(0x5a, 0xff)) {
        // Keep PSEL high between transfers; PENABLE returns low for setup.
        dut.apb.psel.poke(true.B)
        dut.apb.penable.poke(false.B)
        dut.apb.pwrite.poke(true.B)
        dut.apb.paddr.poke(0x41003008L.U)
        dut.apb.pwdata.poke((0xab00 | value).U)
        tx.expect(false.B)
        rx.expect(false.B)
        dut.apb.pready.expect(false.B)
        dut.clock.step()
        data.expect(previous.U)
        dut.apb.penable.poke(true.B)
        dut.apb.pready.expect(true.B)
        dut.apb.pslverr.expect(false.B)
        tx.expect(true.B)
        rx.expect(false.B)
        data.expect(previous.U) // registered data changes at the closing edge
        dut.clock.step()
        data.expect(value.U)
        previous = value
        dut.apb.penable.poke(false.B)
        tx.expect(false.B)
      }
      dut.apb.pwrite.poke(false.B)
      dut.clock.step()
      dut.apb.penable.poke(true.B)
      tx.expect(false.B)
      rx.expect(true.B)
      dut.apb.prdata.expect(0xa5.U)
      dut.clock.step()
      dut.apb.psel.poke(false.B)
      rx.expect(false.B)
      // PENABLE by itself must not write, trigger or acknowledge anything.
      dut.apb.pwrite.poke(true.B)
      dut.apb.pwdata.poke(0.U)
      dut.clock.step(2)
      data.expect(0xff.U)
      tx.expect(false.B)
      dut.apb.pready.expect(false.B)
      // Reset suppresses a pending write and restores defined state.
      dut.apb.psel.poke(true.B)
      dut.apb.paddr.poke(0x41003000L.U)
      dut.apb.pwdata.poke(3.U)
      dut.reset.poke(true.B)
      dut.apb.pready.expect(false.B)
      dut.clock.step()
      uint(dut, "uart0.ctrl.en").expect(0.U)
      dut.apb.psel.poke(false.B)
      dut.apb.penable.poke(false.B)
      dut.reset.poke(false.B)
      bfm.readExpect(0x41003000L, Some(0))
      // Setup alone must not change an RW register.
      dut.apb.psel.poke(true.B)
      dut.apb.pwrite.poke(true.B)
      dut.apb.paddr.poke(0x41003000L.U)
      dut.apb.pwdata.poke(3.U)
      dut.clock.step(3)
      uint(dut, "uart0.ctrl.en").expect(0.U)
    }
  }

  // A second workbook exercises cases not present in soc.xlsx. Numeric cells
  // are intentional: POI exposes them to Sheet as strings such as "4096.0".
  private def withFixture(body: String => Unit): Unit = {
    val file = Files.createTempFile("csr-test-", ".xlsx")
    val workbook = new XSSFWorkbook()
    def sheet(name: String, rows: Seq[Seq[Any]]): Unit = {
      val s = workbook.createSheet(name)
      rows.zipWithIndex.foreach { case (values, i) =>
        val row = s.createRow(i)
        values.zipWithIndex.foreach { case (value, j) =>
          val cell = row.createCell(j)
          value match {
            case n: Int => cell.setCellValue(n.toDouble)
            case other => cell.setCellValue(other.toString)
          }
        }
      }
    }
    sheet("Map", Seq(Seq("Block", "Name", "Base Address", "End Address"),
      Seq("Example", "example", 4096, 4351)))
    sheet("Example", Seq(Seq("Register", "Offset", "Field", "Type", "Range", "Init"),
      Seq("mixed", 0, "config", "rw", "11:4", "0x83"),
      Seq("mixed", 0, "status", "ro", "19:16", "?"),
      Seq("mixed", 0, "id", "const", "31:28", "0xa"),
      Seq("send", 4, "", "wotrg", "15:8", "0x3c"),
      Seq("receive", 8, "", "rotrg", "31:24", "?"),
      Seq("constant", 12, "", "const", "7:0", 7)))
    val out = Files.newOutputStream(file)
    try workbook.write(out) finally { out.close(); workbook.close() }
    try body(file.toString) finally Files.deleteIfExists(file)
  }

  it should "generate arbitrary names, nonzero bit ranges, mixed types and whole-register triggers" in {
    withFixture { path =>
      test(new CsrAdapter(path)) { dut =>
        val bfm = master(dut)
        bfm.reset()
        uint(dut, "example.mixed.status").poke(5.U)
        uint(dut, "example.receive.data").poke(0x80.U)
        uint(dut, "example.send.data").expect(0x3c.U)
        bfm.readExpect(0x1000, Some(0xa0050830L))
        assert(bfm.write(0x1000, 0xffffffffL).nonEmpty)
        bfm.readExpect(0x1000, Some(0xa0050ff0L))
        uint(dut, "example.mixed.config").expect(0xff.U)
        bfm.readExpect(0x1004, None)
        assert(bfm.write(0x1004, 0x12345678L).nonEmpty)
        uint(dut, "example.send.data").expect(0x56.U)
        bfm.readExpect(0x1008, Some(0x80000000L))
        assert(bfm.write(0x1008, 0).isEmpty)
        bfm.readExpect(0x100c, Some(7))
        assert(bfm.write(0x100c, 0).isEmpty)
        val mixed = signal(dut, "example.mixed").asInstanceOf[DynamicBundle]
        assert(!mixed.elements.contains("id"))
        bfm.reset()
        uint(dut, "example.send.data").expect(0x3c.U)
        bfm.readExpect(0x1000, Some(0xa0050830L))
      }
    }
  }
}
