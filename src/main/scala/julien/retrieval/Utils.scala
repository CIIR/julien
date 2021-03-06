package julien
package retrieval

import scala.collection.mutable.PriorityQueue
import scala.collection.JavaConversions._
import julien._
import julien.access._
import julien.eval.QueryResult

import java.io.PrintStream

/** Provides printing methods for lists of [[ScoredDocument]]s.
  * Most likely will be factored out in the future.
  */
object Utils {
  // TODO: Remove this, make the ScoredDocuments into a collection type.

  def printResults(
    results: QueryResult[ScoredDocument],
    index: Index,
    out: PrintStream = Console.out) : Unit = {
    for (sd <- results) {
      val name = index.name(sd.id)
      out.println(f"test $name ${sd.score}%10.8f ${sd.rank} julien")
    }
  }
}

