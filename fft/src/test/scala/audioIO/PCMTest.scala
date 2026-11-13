package audioio

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.util.Random

class PCMTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior.of("PCM")

  private val pdmWordWidth = 16
  private val decimation = 10
  private val totalBits = pdmWordWidth * decimation

  private def expectedPcm(ones: Int): Int = {
    val centered = ones - totalBits / 2
    centered * 127 / (totalBits / 2)
  }

  private def wordWithOnes(ones: Int): BigInt = {
    require(ones >= 0 && ones <= pdmWordWidth)
    if (ones == 0) BigInt(0)
    else if (ones == pdmWordWidth) (BigInt(1) << pdmWordWidth) - 1
    else (BigInt(1) << ones) - 1
  }

  private def randomWordWithOnes(ones: Int, random: Random): BigInt = {
    require(ones >= 0 && ones <= pdmWordWidth)
    random
      .shuffle((0 until pdmWordWidth).toList)
      .take(ones)
      .foldLeft(BigInt(0))((word, bit) => word | (BigInt(1) << bit))
  }

  private def driveWord(
      dut: PCM,
      word: BigInt,
      valid: Boolean = true
  ): Unit = {
    dut.io.pdmWord.poke(word.U)
    dut.io.pdmValid.poke(valid.B)
    dut.clock.step()
    dut.io.pdmValid.poke(false.B)
  }

  it should "convert PDM density into signed PCM samples" in {
    test(new PCM(pdmWordWidth, decimation)) { dut =>
      val densities =
        Seq.fill(decimation)(0) ++
          Seq.fill(decimation)(16) ++
          Seq.fill(decimation)(8)
      val produced = densities.grouped(decimation).map { group =>
        group.foreach(ones => driveWord(dut, wordWithOnes(ones)))
        dut.io.pcmValid.expect(true.B)
        val sample = dut.io.pcmSample.peek().litValue.toInt
        sample
      }.toSeq

      assert(produced == Seq(-127, 127, 0))
    }
  }

  it should "ignore PDM words when pdmValid is low" in {
    test(new PCM(pdmWordWidth, decimation)) { dut =>
      dut.io.pdmWord.poke(wordWithOnes(16).U)
      dut.io.pdmValid.poke(false.B)

      for (_ <- 0 until decimation) {
        dut.clock.step()
        dut.io.pcmValid.expect(false.B)
      }
    }
  }

  it should "convert reproducible random PDM words correctly" in {
    val random = new Random(0x5eed)
    val words = Seq.fill(decimation * 25) {
      BigInt(pdmWordWidth, random)
    }

    test(new PCM(pdmWordWidth, decimation)) { dut =>
      words.grouped(decimation).foreach { group =>
        group.foreach(word => driveWord(dut, word))

        val expectedOnes = group.map(_.bitCount).sum
        val expected = expectedPcm(expectedOnes)

        dut.io.pcmValid.expect(true.B)
        dut.io.pcmSample.expect(expected.S)
      }
    }
  }

  it should "write a PDM and PCM waveform visualization" in {
    test(new PCM(pdmWordWidth, decimation)) { dut =>
      val random = new Random(0xface)
      val sampleCount = 32
      val targetDensities = (0 until sampleCount).map { sample =>
        val waveform =
          math.sin(2.0 * math.Pi * 2.0 * sample / sampleCount) +
            0.2 * math.sin(2.0 * math.Pi * 5.0 * sample / sampleCount)
        val normalized = waveform / 1.2
        math.max(0, math.min(pdmWordWidth,
          math.round(pdmWordWidth / 2.0 + (pdmWordWidth / 2.0 - 1) * normalized).toInt))
      }
      val words = targetDensities.flatMap { ones =>
        Seq.fill(decimation)(randomWordWithOnes(ones, random))
      }

      val rows = scala.collection.mutable.ArrayBuffer[String](
        "sample,pdm_word,ones,pcm_valid,pcm"
      )
      val samples = scala.collection.mutable.ArrayBuffer[Int]()

      words.grouped(decimation).zipWithIndex.foreach { case (group, sampleIndex) =>
        group.foreach { word =>
          driveWord(dut, word)
          val pcmValid = dut.io.pcmValid.peek().litToBoolean
          val pcm = dut.io.pcmSample.peek().litValue.toInt
          val ones = word.bitCount

          rows += s"$sampleIndex,${word.toString(2).reverse.padTo(pdmWordWidth, '0').reverse},$ones,$pcmValid,$pcm"
          if (pcmValid) samples += pcm
        }
      }

      val outputDir = Paths.get("target", "pcm")
      Files.createDirectories(outputDir)
      val csv = outputDir.resolve("pcm-waveform.csv")
      Files.write(csv, rows.mkString("\n").getBytes(StandardCharsets.UTF_8))

      println(s"\nPCM waveform written to ${csv.toAbsolutePath}")
      println("PDM density and PCM amplitude:")
      samples.zipWithIndex.foreach { case (pcm, index) =>
        val bar = if (pcm >= 0) "+" * (pcm / 4) else "-" * (-pcm / 4)
        println(f"$index%02d ${pcm}%4d $bar")
      }

      val expected = words.grouped(decimation).map { group =>
        expectedPcm(group.map(_.bitCount).sum)
      }.toSeq

      assert(samples == expected)
      assert(Files.exists(csv))
      assert(Files.readAllLines(csv).size() == rows.size)
    }
  }
}
