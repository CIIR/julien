package julien
package retrieval

import julien.galago.core.index.LengthsReader._

object IndexLengths {
  def apply() = new IndexLengths(None)
  def apply(f: String) = new IndexLengths(Some(f))
}

class IndexLengths private (field: Option[String])
    extends IteratedHook[LengthsIterator]
    with LengthsView {
  override def toString =
    s"lengths:" + (if (isAttached) index.toString else "")
  def getIterator(i: Index): LengthsIterator = i.lengthsIterator(field)
  def length: Int = it.get.getCurrentLength
  def current: Int = it.get.currentCandidate
}
