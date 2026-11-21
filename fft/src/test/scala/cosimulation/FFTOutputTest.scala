
package cosimulation

import chisel3._
import chiseltest._
import fft.{DFT, FFTComb}
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.file.{Files, Path}
import _root_.util.{SimulatorTestUtils, TestFiles}
import _root_.util.TestFiles.{writeFloat, writeHex}
import scala.sys.process._

class FFTOutputTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "FFT output generation"

  private val projectRoot = TestFiles.projectRoot
  private val coSimulationDir = projectRoot.resolve("co-simulation")
  private val outputDir = projectRoot.resolve("co-simulation/output_hex")
  private val defaultInput = coSimulationDir.resolve("generated_hex/32/sine.hex")
  private val inputPath = sys.env.get("FFT_INPUT")
    .map(value => projectRoot.resolve(value).normalize()).getOrElse(defaultInput)

  private val simulator = SimulatorTestUtils.annotations

  private def readInput(path: Path): Seq[Int] = {
    if (!Files.isRegularFile(path) && !sys.env.contains("FFT_INPUT")) {
      val python = sys.env.getOrElse("FFT_PYTHON", "python")
      val status = Process(Seq(python, coSimulationDir.resolve("generate_hex.py").toString,
        "--waveform", "sine", "--samples", "32"), projectRoot.toFile).!
      require(status == 0, s"Waveform generation failed ($status); run make sine or set FFT_PYTHON")
    }
    TestFiles.readSigned8(path, 32)
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
