package julien
package retrieval
package processor

import language.implicitConversions
import julien.retrieval._
import julien.behavior._

trait Preparer {
  def root: Feature

  implicit def np2op(np: NeedsPreparing): Operator = np.asInstanceOf[Operator]

  def prepare(): Unit = {
    val unprepped: Seq[NeedsPreparing] = root.grab[NeedsPreparing].distinct
    if (unprepped.size > 0) {
      // We now need to get the iterators of the unprepped nodes, zip down them
      // and update statistics until done, then reset.
      val iterators: Array[Movable] =
        unprepped.flatMap(_.movers).toSet.filterNot(_.isDense).toArray
      // Make all needed iterators are ready to run
      iterators.foreach(_.reset)

      while (!QueryProcessor.isDone(iterators)) {
        val activeBuf = Array.newBuilder[Movable]
        var l=0
        while (l < iterators.length) {
          val curItr = iterators(l)
          if (!curItr.isDone) {
            activeBuf += curItr
          }
          l += 1
        }
        val active = activeBuf.result()
        val numActive = active.length
        var k=0
        var candidate = Int.MaxValue
        while (k < numActive) {
          val curVal = active(k).at
          if (curVal < candidate) {
            candidate = curVal
          }
          k += 1
        }

        var i = 0
        while (i < numActive) {
          active(i).moveTo(candidate)
          i += 1
        }

        for (p <- unprepped) {
          p.updateStatistics(candidate)
        }

        var j = 0
        while (j < numActive) {
          active(j).movePast(candidate)
          j += 1
        }
      }
      unprepped.foreach(_.prepared)
    }

    // Do this regardless in case any iterators are recycled.
    val movers = root.movers.toSet
    movers.foreach(_.reset)
  }
}
