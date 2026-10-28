
package comparison

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorBackendAnnotation
import org.scalatest.flatspec.AnyFlatSpec

import engine.{FFTFP, FFTRadix2}
import fft.{FFTComb, DFT}

import java.nio.file.{Files, Path, Paths}
import scala.io.Source

class FFTOutputTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "FFT output generation"

  private val outputDir = Paths.get("co-simulation")

  private val inputPath = Paths.get(
    sys.env.getOrElse("FFT_INPUT", outputDir.resolve("sine.hex").toString)
  )

  private val simulator = sys.env.get("FFT_SIMULATOR") match {
    case Some("verilator") => Seq(VerilatorBackendAnnotation)
    case None | Some("treadle") => Seq.empty
    case Some(other) =>
      throw new IllegalArgumentException(s"Unknown FFT_SIMULATOR: $other")
  }

  private def readInput(path: Path): Seq[Int] = {
    val source = Source.fromFile(path.toFile)
    try {
      source.getLines().map(_.trim).filter(_.nonEmpty).map { line =>
        val raw = Integer.parseInt(line, 16)
        require(raw >= 0 && raw < 256, "Expected 8-bit HEX input")
        if (raw >= 128) raw - 256 else raw
      }.toVector
    } finally {
      source.close()
    }
  }

  private def writeHex(path: Path, values: Seq[Int], bits: Int): Unit = {
    val digits = (bits + 3) / 4
    val mask = (1L << bits) - 1

    val lines = values.map { value =>
      ("%0" + digits + "X").format(value.toLong & mask)
    }

    Files.write(path, (lines.mkString("\n") + "\n").getBytes("UTF-8"))
  }

  private def writeFloat(path: Path, values: Seq[Double]): Unit = {
    val lines = values.map(_.toString)
    Files.write(path, (lines.mkString("\n") + "\n").getBytes("UTF-8"))
  }

  it should "export FFT results from all implementations" in {
    Files.createDirectories(outputDir)

    val input = readInput(inputPath)
    require(input.length == 32, "Expected 32 input samples")

    // Scala floating-point FFT
    val fftFP = new FFTFP()
    val (fpRe, fpIm) = fftFP.fft(input.map(_.toDouble))

    writeFloat(outputDir.resolve("scala_fp_real.txt"), fpRe)
    writeFloat(outputDir.resolve("scala_fp_imag.txt"), fpIm)

    // Scala integer Radix-2 FFT
    val fftRadix2 = new FFTRadix2()
    val (radixRe, radixIm) = fftRadix2.fft(input)

    writeHex(outputDir.resolve("scala_radix2_real.hex"), radixRe, 32)
    writeHex(outputDir.resolve("scala_radix2_imag.hex"), radixIm, 32)

    // Chisel FFT
    test(new FFTComb()).withAnnotations(simulator) { dut =>
      for (i <- input.indices) {
        dut.io.input(i).poke(input(i).S)
      }

      val real = (0 until 32).map { i => dut.io.outputRe(i).peek().litValue.toInt }

      val imag = (0 until 32).map { i => dut.io.outputIm(i).peek().litValue.toInt }

      writeHex(outputDir.resolve("chisel_fft_real.hex"), real, dut.outputWidth)
      writeHex(outputDir.resolve("chisel_fft_imag.hex"), imag, dut.outputWidth)
    }

    // Chisel DFT
    test(new DFT()).withAnnotations(simulator) { dut =>
      for (i <- input.indices) {
        dut.io.input(i).poke(input(i).S)
      }

      val real = (0 until 32).map { i => dut.io.outputRe(i).peek().litValue.toInt }
      val imag = (0 until 32).map { i => dut.io.outputIm(i).peek().litValue.toInt }

      writeHex(outputDir.resolve("chisel_dft_real.hex"), real, dut.outputWidth)
      writeHex(outputDir.resolve("chisel_dft_imag.hex"), imag, dut.outputWidth)
    }

    println(s"FFT results exported to ${outputDir.toAbsolutePath}")
  }
}
