package top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class UartTopTest extends AnyFlatSpec with ChiselScalatestTester {
  "UartTopTest" should "pass" in {
    val freq = 10
    val baud = 1
    val samples = 32
    test(new UartTop(freq, baud, samples)) { dut =>
      // setup
      dut.io.rx.poke(true.B)
      dut.clock.setTimeout(10000)
      // reset
      dut.reset.poke(true.B)
      dut.clock.step(10)
      dut.reset.poke(false.B)
      dut.clock.step(10)
      
      // transmit 0 to 31
      for (i <- 0 until samples) {
        dut.clock.step(10)
        val byte = i
        println(s"transmit byte: ${byte}")
        transmit(dut, freq, baud, byte)
      }

      // receive and expect 0 to 31
      for (i <- 0 until samples) {
        val byte = receive(dut, freq, baud)
        println(s"receive byte: ${byte}")
        assert(byte == i)
      }
    }
  }

  def transmit(dut: UartTop, freq: Int, baud: Int, byte: Int) {
    val cyclesPerBaud: Int = (freq + baud / 2) / baud
    // send synchronization bit
    dut.io.rx.poke(false.B)
    dut.clock.step(cyclesPerBaud)
    // send bits
    var bits = byte
    for (i <- 0 until 8) {
      dut.io.rx.poke(((bits & 1) == 1).B)
      bits = bits >> 1
      dut.clock.step(cyclesPerBaud)
    }
    // reset rx line
    dut.io.rx.poke(true.B)
  }

  def receive(dut: UartTop, freq: Int, baud: Int): Int = {
    val cyclesPerBaud: Int = (freq + baud / 2) / baud
    val startCylces: Int = (3 * freq / 2 + baud / 2) / baud
    // wait for synchronization
    while (dut.io.tx.peek().litValue == 1) {
      dut.clock.step(1)
    }
    // wait for synchronization bit
    dut.clock.step(startCylces)
    // receive bits
    var bits: Int = 0
    for (i <- 0 until 8) {
      bits = (bits >> 1) + (dut.io.tx.peek().litValue.toInt << 7)
      dut.clock.step(cyclesPerBaud)
    }
    return bits
  }
}
