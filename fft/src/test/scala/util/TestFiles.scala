package util

import java.nio.file.{Files, Path, Paths}
import scala.io.Source
import scala.jdk.CollectionConverters._

object TestFiles {
  // sbt runs at the project root; also support launches from a child directory.
  def projectRoot: Path = Iterator.iterate(Paths.get("").toAbsolutePath)(_.getParent)
    .takeWhile(_ != null)
    .find(path => Files.isRegularFile(path.resolve("build.sbt")) &&
      Files.isDirectory(path.resolve("co-simulation")))
    .getOrElse(throw new IllegalArgumentException("Cannot locate FFT project root"))

  def sortedChildren(directory: Path): Vector[Path] = {
    val stream = Files.list(directory)
    try stream.iterator().asScala.toVector.sortBy(_.getFileName.toString)
    finally stream.close()
  }

  def readSigned8(path: Path, samples: Int): Vector[Int] = {
    require(Files.isRegularFile(path), s"Missing FFT input: $path; run make generate-hex-all")
    val source = Source.fromFile(path.toFile, "US-ASCII")
    val values = try {
      source.getLines().zipWithIndex.map { case (line, index) =>
        require(line.matches("[0-9a-fA-F]{2}"),
          s"$path:${index + 1}: expected exactly one two-digit 8-bit HEX sample")
        val raw = Integer.parseInt(line, 16)
        if (raw >= 128) raw - 256 else raw
      }.toVector
    } finally source.close()
    require(values.length == samples,
      s"$path: expected $samples samples, got ${values.length}")
    values
  }

  def writeHex(path: Path, values: Seq[Int], bits: Int): Unit = {
    val digits = (bits + 3) / 4
    val mask = (1L << bits) - 1

    val lines = values.map { value => ("%0" + digits + "X").format(value.toLong & mask) }

    Files.write(path, (lines.mkString("\n") + "\n").getBytes("UTF-8"))
  }

  def writeFloat(path: Path, values: Seq[Double]): Unit = {
    val lines = values.map(_.toString)
    Files.write(path, (lines.mkString("\n") + "\n").getBytes("UTF-8"))
  }
}
