package cosimulation

case class ComplexFP(re: Double, im: Double) {
  def +(that: ComplexFP): ComplexFP = ComplexFP(re + that.re, im + that.im)
  override def toString: String = s"($re, $im)"
}