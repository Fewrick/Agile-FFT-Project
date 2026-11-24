package util

import chiseltest.simulator.VerilatorBackendAnnotation

object SimulatorTestUtils {
  def annotations = sys.env.get("FFT_SIMULATOR") match {
    case Some("verilator") => Seq(VerilatorBackendAnnotation)
    case None | Some("treadle") => Seq.empty
    case Some(other) =>
      throw new IllegalArgumentException(s"Unknown FFT_SIMULATOR: $other")
  }
}
