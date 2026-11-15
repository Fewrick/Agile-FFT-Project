package cosimulation

case class ComplexValue(re: Int, im: Int) {
  override def toString: String = s"($re, $im)"
}