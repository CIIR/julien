package julien
package eval

class CountRetrieved extends QueryEvaluator() {
  def eval[T <: ScoredObject](
    result: QueryResult[T],
    judgment: QueryJudgments,
    strictlyEval: Boolean): Double = result.size.toDouble

  val name: String = "Retrieved"
}
