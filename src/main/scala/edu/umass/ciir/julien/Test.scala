package edu.umass.ciir.julien

import scala.collection.mutable.PriorityQueue
import edu.umass.ciir.julien.Utils._
import scala.collection.JavaConversions._
import org.lemurproject.galago.tupleflow.Parameters

object Test {
  implicit def term2op(t: Term): SingleTermView = SingleTermView(t)
  type TMap = Map[Operator, Set[Term]]

  def main(args: Array[String]): Unit = {
    val params = new Parameters(args)
    val query = params.getString("query").split(" ").map(Term(_))
    val ql = Combine(query.map(a => Dirichlet(a, LengthsView())): _*)
    val sdm =
      Combine(
        Weight(Combine(query.map(a => Dirichlet(a,LengthsView())): _*), 0.8),
        Weight(Combine(query.sliding(2,1).map { p =>
          Dirichlet(OrderedWindow(1, p: _*), LengthsView())
        }.toSeq: _*), 0.15),
        Weight(Combine(query.sliding(2,1).map { p =>
          Dirichlet(UnorderedWindow(8, p: _*), LengthsView())
        }.toSeq: _*), 0.05)
      )

    // Assign iterators to each of the underlying terms
    val indexType = params.getString("type")
    assert("""memory|disk""".r matches indexType,
      s"Unknown index type: $indexType")
    val index : Index = indexType match {
      case "disk" => Index.disk(params.getString("disk"))
      case "memory" => Index.memory(params.getString("memory"))
    }

    val processor = SimpleProcessor()
    // Connect to this index
    val results =
      time("Exec time") {
        index.attach(sdm)
        processor.add(sdm)
        processor.run
      }
    printResults(results, index)
  }
}