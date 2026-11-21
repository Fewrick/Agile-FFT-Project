package cosimulation

import chiseltest._
import _root_.util.{SeqTransformHarness, SeqTransformTestUtils, SimulatorTestUtils, TestFiles}
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.file.{Files, Path}

/** Run existing vectors with sbt "testOnly cosimulation.GeneratedHexTest".
  * Generate inputs first with make generate-hex-all SAMPLES=32 (or another FFT size).
  * Set FFT_SIMULATOR=verilator to use the Verilator backend.
  */
class GeneratedHexTest extends AnyFlatSpec with ChiselScalatestTester {
  private val simulator = SimulatorTestUtils.annotations

  private def discover(): Vector[(Int, Vector[Path])] = {
    val root = TestFiles.projectRoot.resolve("co-simulation/generated_hex")
    require(Files.isDirectory(root), s"Missing $root; run make generate-hex-all")
    val directories = TestFiles.sortedChildren(root).filter(path => Files.isDirectory(path))
    val cases = directories.flatMap { directory =>
      val files = TestFiles.sortedChildren(directory).filter(path =>
        Files.isRegularFile(path) && path.getFileName.toString.endsWith(".hex"))
      if (files.isEmpty) None
      else {
        val name = directory.getFileName.toString
        require(name.matches("[0-9]+"), s"$directory: expected a sample-count directory")
        val samples = name.toIntOption.getOrElse(
          throw new IllegalArgumentException(s"$directory: sample count is too large"))
        require(samples >= 2 && (samples & (samples - 1)) == 0,
          s"$directory: FFT size must be a power of two, at least 2")
        Some((samples, files))
      }
    }.sortBy(_._1)
    require(cases.nonEmpty, s"No HEX vectors found under $root; run make generate-hex-all")
    cases
  }

  for (inverse <- Seq(false, true)) {
    val direction = if (inverse) "IFFTSeq" else "FFTSeq"
    direction should "match FFTRadix2 for every generated HEX vector" in {
      for ((samples, paths) <- discover()) {
        // Read and validate every file before starting the simulator for this size.
        val frames = paths.map(path => path -> TestFiles.readSigned8(path, samples).map(value => ComplexValue(value, 0)))
        val width = 8
        test(new SeqTransformHarness(width, samples, inverse)).withAnnotations(simulator) { dut =>
          dut.clock.setTimeout(SeqTransformTestUtils.frameTimeout(samples))
          for (((path, input), frame) <- frames.zipWithIndex) {
            info(s"$direction: $samples samples, ${path.getFileName}")
            withClue(s"$direction, $path: ") {
              SeqTransformTestUtils.checkFrame(dut, input, width, samples, inverse,
                useStalls = frame % 2 == 1)
            }
          }
        }
      }
    }
  }
}
