package operators

abstract class MultiTermOp(terms: Term*) extends PositionsOp {
  // Lazily verifies that all terms
  lazy val verified = {
    val r = terms.forall { t => t.attachedIndex == terms.head.attachedIndex }
    assume(r, s"Tried to use multi-term op from different indexes.")
    true // If we made it here, must be true
  }
  def count: Count = new Count(this.positions.size)
  lazy val statistics: CountStatistics = {
    assume(verified) // precondition which means we can use any of them
    val index = terms.head.attachedIndex

    // ...
  }
}
