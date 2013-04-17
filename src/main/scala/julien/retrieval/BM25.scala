package julien
package retrieval

object BM25 {
  private val defB = 0.75
  private val defK = 1.2
  def apply(
    op: PositionStatsView,
    l: LengthsView,
    b: Double = defB,
    k: Double = defK
  ): BM25 = new BM25(op, l, op, defB, defK)
  def apply(c: CountView, l: LengthsView, s: StatisticsView): BM25 =
    new BM25(c, l, s, defB, defK)
  def apply(
    c: CountView,
    l: LengthsView,
    s: StatisticsView,
    b: Double,
    k: Double) = new BM25(c, l, s, b, k)
}

class BM25(
  val op: CountView,
  val lengths: LengthsView,
  val statsrc: StatisticsView,
  val b: Double,
  val k: Double)
    extends FeatureOp {
  require(b > 0.0 && b < 1.0, s"b must be in [0,1]. Got $b")
  require(k > 0.0, s"k must be positive. Got $k")
  lazy val children: Seq[Operator] = Set[Operator](op, lengths, statsrc).toList
  lazy val views: Set[ViewOp] = Set[ViewOp](op, lengths, statsrc)

  // Runs when asked for the first time, and runs only once
  lazy val stats = statsrc.statistics
  lazy val avgDocLength = stats.collLength / stats.numDocs
  lazy val idf = scala.math.log(stats.numDocs / (stats.docFreq + 0.5))

  // Yay - plays nice w/ the bounds.
  override val lowerBound: Score = new Score(0)
  override lazy val upperBound: Score = {
    val maxtf = statsrc.statistics.max
    score(maxtf, maxtf)
  }

  def eval: Score = score(op.count, lengths.length)
  def score(c: Count, l: Length) = {
    val num = c + (k + 1)
    val den = c + (k * (1 - b + (b * l / avgDocLength)))
    new Score(idf * num / den)
  }
}

