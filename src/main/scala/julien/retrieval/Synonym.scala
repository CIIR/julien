package julien
package retrieval

import scala.collection.mutable.ArrayBuffer
import galago.core.util.ExtentArray

object Synonym {
  def apply(terms: Term*) = new Synonym(terms)
}

class Synonym(terms: Seq[Term])
    extends MultiTermView(terms) {

  override def positions: ExtentArray = {
    val arrays = terms.map(_.underlying.extents)
    val b = ArrayBuffer[Int]()
    for (a <- arrays) {
      while (a.hasNext) b += a.next
    }

    val hits = new ExtentArray()
    for (p <- b.sorted) hits.add(p)
    hits
  }
}
