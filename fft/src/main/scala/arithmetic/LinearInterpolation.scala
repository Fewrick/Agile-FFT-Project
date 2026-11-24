import chisel3._
import chisel3.util._

class LinearInterpolation(val dataWidth: Int, val samples: Int, val overlapSamples: Int) extends Module {
    require(isPow2(samples))
    require(isPow2(overlapSamples))
    require(overlapSamples >= 2)
    require(overlapSamples <= samples / 2)

    // O = overlapSamples = 2^K
    // Division by O can therefore be implemented as a right shift by K
    private val shift = log2Ceil(overlapSamples)

    val io = IO(new Bundle {
        val a = Input(SInt(dataWidth.W)) // Previous frame sample
        val b = Input(SInt(dataWidth.W)) // Current frame sample

        val index = Input(UInt(shift.W)) // Position i within the overlap [0, O-1]

        val out = Output(SInt(dataWidth.W))
    })

    // Linear interpolation between two samples:
    //
    // y = (1 - alpha)*a + alpha*b
    //   = a + alpha*(b - a)
    //
    // With alpha = i/O and O = 2^K:
    //
    // y = a + i*(b - a)/2^K

    // Calculate the difference d = b - a.
    // Extend the bit width to prevent signed overflow.
    val diff = io.b -& io.a

    // Decompose the index i into its binary representation:
    //
    // i = sum(i_k * 2^k), for k = 0, ..., K-1
    //
    // Consequently:
    //
    // i*d = sum(i_k * d * 2^k)
    //
    // Each term is either zero or a statically shifted difference,
    // eliminating the need for a dedicated hardware multiplier.
    val terms = (0 until shift).map { k =>
        Mux(io.index(k), diff << k, 0.S)
    }

    // Sum the partial products using an adder tree:
    // weighted = i*(b - a)
    val weighted = terms.reduceTree(_ +& _)

    // Normalize by O = 2^K using an arithmetic right shift
    // and add the previous frame sample:
    //
    // y = a + (i*(b - a) >> K)
    //
    // The right shift rounds toward negative infinity.
    io.out := io.a +& (weighted >> shift)
}