
package cosimulation

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import fft.{DFT, FFTComb}
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.file.{Files, Path, Paths}
import scala.io.Source
import scala.sys.process._

class FFTOutputTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "FFT output generation"

  // sbt runs at the project root; also support launches from a child directory.
  private val projectRoot = Iterator.iterate(Paths.get("").toAbsolutePath)(_.getParent)
    .takeWhile(_ != null)
    .find(path => Files.isRegularFile(path.resolve("build.sbt")) &&
      Files.isDirectory(path.resolve("co-simulation")))
    .getOrElse(throw new IllegalArgumentException("Cannot locate FFT project root"))
  private val coSimulationDir = projectRoot.resolve("co-simulation")
  private val outputDir = projectRoot.resolve("co-simulation/output_hex")
  private val defaultInput = coSimulationDir.resolve("generated_hex/32/sine.hex")
  private val inputPath = sys.env.get("FFT_INPUT")
    .map(value => projectRoot.resolve(value).normalize()).getOrElse(defaultInput)

  private val simulator = sys.env.get("FFT_SIMULATOR") match {
    case Some("verilator") => Seq(VerilatorBackendAnnotation)
    case None | Some("treadle") => Seq.empty
    case Some(other) =>
      throw new IllegalArgumentException(s"Unknown FFT_SIMULATOR: $other")
  }

  private def readInput(path: Path): Seq[Int] = {
    if (!Files.isRegularFile(path) && !sys.env.contains("FFT_INPUT")) {
      val python = sys.env.getOrElse("FFT_PYTHON", "python")
      val status = Process(Seq(python, coSimulationDir.resolve("generate_hex.py").toString,
        "--waveform", "sine", "--samples", "32"), projectRoot.toFile).!
      require(status == 0, s"Waveform generation failed ($status); run make sine or set FFT_PYTHON")
    }
    require(Files.isRegularFile(path), s"Missing FFT input: $path; run make sine")
    val source = Source.fromFile(path.toFile, "US-ASCII")
    try {
      source.getLines().zipWithIndex.map { case (line, index) =>
        require(line.matches("[0-9a-fA-F]{2}"),
          s"$path:${index + 1}: expected exactly one two-digit 8-bit HEX sample")
        val raw = Integer.parseInt(line, 16)
        if (raw >= 128) raw - 256 else raw
      }.toVector
    } finally {
      source.close()
    }
  }

  private def writeHex(path: Path, values: Seq[Int], bits: Int): Unit = {
    val digits = (bits + 3) / 4
    val mask = (1L << bits) - 1

    val lines = values.map { value => ("%0" + digits + "X").format(value.toLong & mask) }

    Files.write(path, (lines.mkString("\n") + "\n").getBytes("UTF-8"))
  }

  private def writeFloat(path: Path, values: Seq[Double]): Unit = {
    val lines = values.map(_.toString)
    Files.write(path, (lines.mkString("\n") + "\n").getBytes("UTF-8"))
  }

  it should "export FFT results from all implementations" in {
    Files.createDirectories(outputDir)

    val input = readInput(inputPath)
    require(input.length == 32, s"$inputPath: expected 32 input samples, got ${input.length}")

    // Scala floating-point FFT
    val (fp) = FFTFP.fft(input.map(_.toDouble))

    writeFloat(outputDir.resolve("scala_fp_real.txt"), fp.map(_.re))
    writeFloat(outputDir.resolve("scala_fp_imag.txt"), fp.map(_.im))

    // Scala integer Radix-2 FFT
    val radix = FFTRadix2.fft(input)

    writeHex(outputDir.resolve("scala_radix2_real.hex"), radix.map(_.re), 32)
    writeHex(outputDir.resolve("scala_radix2_imag.hex"), radix.map(_.im), 32)

    // Chisel FFT
    test(new FFTComb()).withAnnotations(simulator) { dut =>
      for (i <- input.indices) {
        dut.io.input(i).poke(input(i).S)
      }

      val real = (0 until 32).map { i => dut.io.output(i).re.peek().litValue.toInt }
      val imag = (0 until 32).map { i => dut.io.output(i).im.peek().litValue.toInt }

      writeHex(outputDir.resolve("chisel_fft_real.hex"), real, dut.outputWidth)
      writeHex(outputDir.resolve("chisel_fft_imag.hex"), imag, dut.outputWidth)
    }

    // Chisel DFT
    test(new DFT()).withAnnotations(simulator) { dut =>
      for (i <- input.indices) {
        dut.io.input(i).poke(input(i).S)
      }

      val real = (0 until 32).map { i => dut.io.output(i).re.peek().litValue.toInt }
      val imag = (0 until 32).map { i => dut.io.output(i).im.peek().litValue.toInt }

      writeHex(outputDir.resolve("chisel_dft_real.hex"), real, dut.outputWidth)
      writeHex(outputDir.resolve("chisel_dft_imag.hex"), imag, dut.outputWidth)
    }

    println(s"FFT results exported to ${outputDir.toAbsolutePath}")
  }
}
