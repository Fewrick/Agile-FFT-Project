package comparison


import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import engine.{FFTFP, FFTRadix2}
import fft.{FFT, DFT}

import java.nio.file.{Files, Path, Paths}
import scala.io.Source

class FFTOutputTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "FFT output comparison"

  private val outputDir = Paths.get("co-simulation")

  // Override using FFT_INPUT=/path/to/input.hex
  private val inputPath = Paths.get(
    sys.env.getOrElse("FFT_INPUT", outputDir.resolve("sine.hex").toString)
  )

  private def signed(value: Int, bits: Int): Int = {
    val mask = (1 << bits) - 1
    val raw = value & mask
    if ((raw & (1 << (bits - 1))) != 0)
      raw - (1 << bits)
    else
      raw
  }

  private def readInput(path: Path): Seq[Int] = {
    val source = Source.fromFile(path.toFile)
    try {
      source.getLines().map(_.trim).filter(_.nonEmpty)
        .map(line => signed(Integer.parseInt(line, 16), 8)).toVector
    } finally {
      source.close()
    }
  }

  private def writeHex(path: Path, values: Seq[Int], bits: Int): Unit = {
    val digits = bits / 4
    val mask = (1L << bits) - 1

    val lines = values.map { value =>
      val raw = value.toLong & mask
      ("%0" + digits + "X").format(raw)
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
    require(input.length == 32, "Chisel FFT/DFT requires 32 samples")

    // Scala floating-point FFT
    val fftFP = new FFTFP()
    val (fpRe, fpIm) = fftFP.fft(input.map(_.toDouble))

    writeFloat(outputDir.resolve("scala_fp_real.txt"), fpRe)
    writeFloat(outputDir.resolve("scala_fp_imag.txt"), fpIm)

    // Scala fixed-point radix-2 FFT
    val fftRadix2 = new FFTRadix2()
    val (radixRe, radixIm) = fftRadix2.fft(input)

    writeHex(outputDir.resolve("scala_radix2_real.hex"), radixRe, 32)
    writeHex(outputDir.resolve("scala_radix2_imag.hex"), radixIm, 32)

    // Chisel FFT
    test(new FFT(32)) { dut =>
      for (i <- input.indices) {
        dut.io.input(i).poke((input(i) & 0xFF).U)
      }

      val real = (0 until 32).map { i =>
        signed(dut.io.outputRe(i).peek().litValue.toInt, 8)
      }

      val imag = (0 until 32).map { i =>
        signed(dut.io.outputIm(i).peek().litValue.toInt, 8)
      }

      writeHex(outputDir.resolve("chisel_fft_real.hex"), real, 8)
      writeHex(outputDir.resolve("chisel_fft_imag.hex"), imag, 8)
    }

    // Chisel DFT
    test(new DFT(32)) { dut =>
      for (i <- input.indices) {
        dut.io.input(i).poke((input(i) & 0xFF).U)
      }

      val real = (0 until 32).map { i =>
        signed(dut.io.outputRe(i).peek().litValue.toInt, 8)
      }

      val imag = (0 until 32).map { i =>
        signed(dut.io.outputIm(i).peek().litValue.toInt, 8)
      }

      writeHex(outputDir.resolve("chisel_dft_real.hex"), real, 8)
      writeHex(outputDir.resolve("chisel_dft_imag.hex"), imag, 8)
    }

    println(s"FFT results exported to ${outputDir.toAbsolutePath}")
  }
}
