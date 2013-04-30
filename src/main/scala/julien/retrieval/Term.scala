package julien
package retrieval

import julien.galago.core.index.ExtentIterator

object Term {
  def apply(s: String) = new Term(s, None)
  def apply(s: String, f: String) = new Term(s, Some(f))
}

/** Represents a direct connection to an index via a key specified
  * the t parameter. Each Term object carries references to the
  * underlying iterator the Term represents, as well as a reference
  * to the index that generated the iterator.
  *
  * These are always 1-to-1.
  */
final class Term private (val t: String, val field: Option[String])
    extends IteratedHook[ExtentIterator]
    with PositionStatsView {

  override def toString: String =
    s"$t: " + (if (isAttached) index.toString else "")

  /** Definition of how this class retrieves its underlying
    * iterator from a given [[Index]] instance.
    */
  def getIterator(i: Index): ExtentIterator = i.shareableIterator(t, field)

  /** Returns the current count of the underlying iterator. */
  def count: Int = underlying.count

  /** Returns the current positions of the underlying iterator. */
  def positions: Positions = Positions(underlying.extents())

  lazy val statistics: CountStatistics = {
    val ns = it.get.asInstanceOf[ARNA].getStatistics
    val cs = attachedIndex.
      lengthsIterator(field).
      asInstanceOf[ARCA].
      getStatistics

    CountStatistics(
      ns.nodeFrequency,
      cs.documentCount,
      cs.collectionLength,
      ns.nodeDocumentCount,
      ns.maximumCount.toInt,
      attachedIndex.collectionStats.maxLength.toInt
    )
  }

  /** Diagnostic for determining the current location of this term. */
  def current: Int = it.get.currentCandidate
}
