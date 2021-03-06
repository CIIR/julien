package julien
package retrieval

import julien.behavior._

object JelinekMercer {
  private val totallyMadeUpValue = 600
  private val defLambda = 0.3

  def apply(op: CountStatsView, l: LengthsView): JelinekMercer =
    apply(op, l, op)

  def apply(
    op: CountStatsView,
    l: LengthsView,
    lambda: Double
  ): JelinekMercer = apply(op, l, op, lambda)

  def apply(
    c: CountView,
    l: LengthsView,
    s: StatisticsView,
    lambda: Double = defLambda
  ): JelinekMercer = if (c.isInstanceOf[Movable])
    new JelinekMercer(c, l, s, lambda) with Driven {
      val driver = c.asInstanceOf[Movable]
    }
    else
      new JelinekMercer(c, l, s, lambda)
}

class JelinekMercer(
  val op: CountView,
  val lengths: LengthsView,
  val statsrc: StatisticsView,
  val lambda: Double)
    extends ScalarWeightedFeature
    with Bounded
{
  require(lambda >= 0.0 && lambda <= 1.0, s"Lambda must be [0,1]. Got: $lambda")
  lazy val children: Array[Operator] =
    Set[Operator](op, lengths, statsrc).toArray
  lazy val views: Set[View] = Set[View](op, lengths, statsrc)

  // Runs when asked for the first time, and runs only once
  lazy val cf = {
    val stats: CountStatistics = statsrc.statistics
    if (stats.collFreq == 0)
      0.5 / stats.collLength
    else
      stats.collFreq.toDouble / stats.collLength
  }

  override lazy val upperBound: Double = {
    val maxtf = statsrc.statistics.max
    score(maxtf, maxtf)
  }

  override lazy val lowerBound: Double =
    //score(0, statsrc.statistics.longestDoc)
    score(0, JelinekMercer.totallyMadeUpValue)

  def eval(id: Int): Double = score(op.count(id), lengths.length(id))
  def score(c: Int, l: Int) =
    scala.math.log((lambda*(c.toDouble/l)) + ((1.0-lambda)*cf))
}


