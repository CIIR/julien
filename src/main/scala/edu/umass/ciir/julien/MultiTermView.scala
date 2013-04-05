package edu.umass.ciir.julien

abstract class MultiTermView(terms: Seq[Term])
    extends PositionsView
    with NeedsPreparing {
  // Make sure we're not making a single view of multiple indexes - that's weird
  val r = terms.forall { t => t.attachedIndex == terms.head.attachedIndex }
  assume(r, s"Tried to use multi-term view from different indexes.")

  def children: Seq[Operator] = terms
  def count: Count = new Count(this.positions.size)

  // Start with no knowledge
  val statistics = CountStatistics()
  statistics.numDocs = terms.head.attachedIndex.numDocuments

  def updateStatistics = {
    val c = count.underlying
    statistics.collFreq += c
    statistics.docFreq += 1
    statistics.max = scala.math.min(statistics.max.underlying, c)
  }
}
