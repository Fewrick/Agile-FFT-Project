package fft

import chisel3.util._
import chiseltest._
import cosimulation.ComplexValue
import _root_.util.{SeqTransformHarness, SeqTransformTestUtils}
import org.scalatest.flatspec.AnyFlatSpec

class Radix2SeqTest extends AnyFlatSpec with ChiselScalatestTester {
  "Radix2Seq memory" should "contain exactly two half-depth complex banks with one read and write each" in {
    for (samples <- Seq(2, 4, 32, 64, 128)) {
      val rtl = (new chisel3.stage.ChiselStage).emitChirrtl(new Radix2Seq(8, samples, false),
        Array("--target-dir", s"test_run_dir/parity_memory_$samples"))
      val memories = "(?m)^\\s*smem .*".r.findAllIn(rtl).toSeq
      assert(memories.size == 2)
      for (bank <- Seq("bank0", "bank1")) {
        val declaration = memories.find(_.contains(s"smem $bank :")).get
        assert(declaration.contains(s"re : SInt<${8 + log2Ceil(samples)}>") &&
          declaration.contains(s"im : SInt<${8 + log2Ceil(samples)}>"))
        assert(declaration.contains(s"[${samples / 2}]"))
        for (access <- Seq("read", "write")) {
          val ports = s"$access mport [^=]+ = $bank\\[".r.findAllIn(rtl).size
          assert(ports == 1, s"$bank has $ports $access ports")
        }
      }
    }
  }

  "Parity banking" should "map each address uniquely and keep every stage conflict-free" in {
    for (samples <- Seq(2, 4, 8, 16, 32, 64, 128, 256, 1024)) {
      def location(address: Int): (Int, Int) =
        (Integer.bitCount(address) & 1, address >> 1)
      val locations = (0 until samples).map(location)
      assert(locations.distinct.size == samples)
      for (bank <- 0 until 2) {
        assert(locations.filter(_._1 == bank).map(_._2).sorted == (0 until samples / 2))
      }
      for (stage <- 0 until log2Ceil(samples)) {
        val half = 1 << stage
        val pairs = (0 until samples / 2).map { index =>
          val low = index & (half - 1)
          val a = ((index & ~(half - 1)) << 1) | low
          (a, a ^ half)
        }
        // A stage is a partition: pending writes cannot alias later reads.
        assert(pairs.flatMap { case (a, b) => Seq(a, b) }.sorted == (0 until samples))
        for ((a, b) <- pairs) assert(location(a)._1 != location(b)._1)
      }
    }
  }

  for (inverse <- Seq(false, true); samples <- Seq(2, 4, 32, 64, 128)) {
    val width = 8
    val direction = if (inverse) "IFFTSeq" else "FFTSeq"
    direction should s"match exact wrapped results at width $width, samples $samples, with stalls and repeated frames" in {
      test(new SeqTransformHarness(width, samples, inverse)) { dut =>
        val random = new scala.util.Random(42)
        val dataWidth = width + (if (inverse) log2Ceil(samples) else 0)
        val limit = 1 << (dataWidth - 1)

        val frames = {
          val basicFrames = Seq(
            // Alternating extremes
            Seq.tabulate(samples)(i =>
              ComplexValue(
                if (i % 2 == 0) -limit else limit - 1,
                if (inverse) (if (i % 3 == 0) -limit else limit - 1) else 0
              )
            ),

            // Random full-range input
            Seq.fill(samples)(
              ComplexValue(
                random.nextInt(2 * limit) - limit,
                if (inverse) random.nextInt(2 * limit) - limit else 0
              )
            ),

            // Negative impulse
            Seq.tabulate(samples)(i =>
              ComplexValue(
                if (i == 0) -1 else 0,
                if (inverse && i == 1) -3 else 0
              )
            )
          )

          val additionalFrames = {
            val amplitude = if (inverse) 16 else math.min(16, limit - 1)

            Seq(
              // All zeros
              Seq.fill(samples)(ComplexValue(0, 0)),

              // Constant signal
              Seq.fill(samples)(ComplexValue(amplitude, 0)),

              // Positive impulse
              Seq.tabulate(samples)(i => ComplexValue(if (i == 0) amplitude else 0, 0)),

              // Impulse in the middle
              Seq.tabulate(samples)(i => ComplexValue(if (i == samples / 2) amplitude else 0, 0)),

              // Alternating signs
              Seq.tabulate(samples)(i => ComplexValue(if (i % 2 == 0) amplitude else -amplitude, 0)),

              // Single-frequency cosine
              Seq.tabulate(samples)(i => ComplexValue(math.round(amplitude * math.cos(2 * math.Pi * 3 * i / samples)).toInt, 0)),

              // Single-frequency sine
              Seq.tabulate(samples)(i => ComplexValue(math.round(amplitude * math.sin(2 * math.Pi * 5 * i / samples)).toInt, 0)),

              // Multiple frequencies
              Seq.tabulate(samples)(i => ComplexValue(math.round((amplitude / 2.0) * (math.sin(2 * math.Pi * 2 * i / samples) + math.cos(2 * math.Pi * 7 * i / samples))).toInt, 0)),

              // Random low-amplitude input
              Seq.fill(samples)(ComplexValue(random.nextInt(2 * amplitude + 1) - amplitude,
                  if (inverse) random.nextInt(2 * amplitude + 1) - amplitude else 0)
              )
            )
          }

          basicFrames ++ additionalFrames
        }

        for ((input, frame) <- frames.zipWithIndex) {
          withClue(s"$direction, samples=$samples, frame=$frame: ") {
            SeqTransformTestUtils.checkFrame(dut, input, width, samples, inverse,
              useStalls = frame % 2 == 1)
            if (frame == 0) info(s"$direction: $samples samples, unstalled frame verified at " +
              s"${SeqTransformTestUtils.frameCycles(samples)} cycles")
          }
        }
      }
    }
  }
}
