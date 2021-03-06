package julien
package retrieval

import scala.collection.mutable.PriorityQueue

object ArrayAccumulator {
  val defaultSize: Int = 1000

  def apply[T <: ScoredObject](
    indexSize:Int,
    numResults:Int): ArrayAccumulator[T] =
    new ArrayAccumulator[T](new Array[Double](indexSize), numResults)

  def apply[T <: ScoredObject](indexSize:Int): ArrayAccumulator[T] =
    new ArrayAccumulator[T](new Array[Double](indexSize), defaultSize)

  /** We assume the provided queue has an ordering of "least is first". */
  def apply[T <: ScoredObject](
    a: Array[Double],
    k: Int = defaultSize): ArrayAccumulator[T] =
    new ArrayAccumulator[T](a, k)
}

/** Implementation of an accumulator with an array underlying the
  * accumulator.
  */
class ArrayAccumulator[T <: ScoredObject] private(
  private[this] val acc: Array[Double],
  l: Int
  ) extends Accumulator[T] {

  override val limit: Option[Int] = Some(l)
  override def clear: Unit = acc.indices.foreach(i => acc(i) = 0.0)

  override def +=(elem: T): this.type = {
    acc(elem.id) += elem.score
    this
  }

  override def iterator: Iterator[T] = new Iterator[T] {
    private var pos = 0
    def hasNext: Boolean = pos < acc.length-1
    def next: T = {
      val toReturn = ScoredDocument(pos, acc(pos))
      pos += 1
      toReturn.asInstanceOf[T]
    }
  }

  /** Produces a List of the result type. Notices that this consumes
    * the contents of the Accumulator.
    */
  override def result(): List[T] = {
    val topK = new PriorityQueue[ScoredDocument]()

    var i = 0
    // pre-load
    while (i < acc.length && topK.size < l) {
      topK += ScoredDocument(i, acc(i))
    }

    // NOW we compare
    while (i < acc.length) {
      val curScore = acc(i)
      if (curScore > topK.head.score) {
        topK.dequeue
        topK += ScoredDocument(i, curScore)
      }
      i += 1
    }

    val b = scala.collection.mutable.ListBuffer[T]()
    // building it back-to-front (i.e. a series of prepends)
    while (!topK.isEmpty) topK.dequeue.asInstanceOf[T] +=: b
    b.result
  }
}
