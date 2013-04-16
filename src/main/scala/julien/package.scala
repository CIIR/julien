import edu.umass.ciir.macros.Macros._
import scala.annotation.elidable


/** Provides classes that are typically used by Julien applications.
  *  ==Overview==
  *
  */
package object julien {

/** Correctness enforced using value classes for any value we might throw
  * around the in the system. Might be a little overkill, but it allows
  * for strong type-checking at compile time.
  *
  *  See SIP-15 (http://docs.scala-lang.org/sips/pending/value-classes.html)
  *  for value class details.
  *
  * Also using SIP-13, implicit classes
  * (http://docs.scala-lang.org/sips/pending/implicit-classes.html)
  */

  /** A belief assigned by a Feature.
    * Underlying class is Double.
    */
  implicit class Score(val underlying: Double) extends AnyVal  {
    def *(l: Long): Score = new Score(underlying * l)
    def *(f: Float): Score = new Score(underlying * f)
    def +(l: Long): Score = new Score(underlying + l)
    def +(f: Float): Score = new Score(underlying + f)
    def -(l: Long): Score = new Score(underlying - l)
    def -(f: Float): Score = new Score(underlying - f)
    def /(l: Long): Score = new Score(underlying / l)
    def /(f: Float): Score = new Score(underlying / f)

    // These are type-erased to cover doubles as well
    def *(s: Score): Score = new Score(underlying * s.underlying)
    def +(s: Score): Score = new Score(underlying + s.underlying)
    def /(s: Score): Score = new Score(underlying / s.underlying)
    def -(s: Score): Score = new Score(underlying - s.underlying)
    // And these are type-erased to cover ints as well
    def *(l: Length): Score = new Score(underlying * l.underlying)
    def +(l: Length): Score = new Score(underlying + l.underlying)
    def -(l: Length): Score = new Score(underlying - l.underlying)
    def /(l: Length): Score = new Score(underlying / l.underlying)
  }
  implicit def score2dbl(s: Score): Double = s.underlying
  implicit object ScoreOrdering extends Ordering[Score] {
    def compare(a: Score, b: Score) = a.underlying compare b.underlying
  }

  /** The number of targets (docs) a particular key (term) occurs in.
    * Underlying class is Long.
    */
  implicit class DocFreq(val underlying: Long) extends AnyVal {
    def +(l: Long): DocFreq = DocFreq(underlying + l)
    def +(i: Int): DocFreq = DocFreq(underlying + i)
    def +(d: Double): Double = d + underlying
  }
  implicit def df2long(df: DocFreq): Long = df.underlying

  /** Number of targets (documents) in the universe (collection).
    * Underlying class is Long.
    */
  implicit class NumDocs(val underlying: Long) extends AnyVal {
    def /(d: Double): Double = underlying.toDouble / d
  }
  implicit def numdocs2long(nd: NumDocs): Long = nd.underlying

  /** Number of keys (terms) in the universe (collection).
    * Underlying class is Long.
    */
  implicit class VocabSize(val underlying: Long) extends AnyVal

  /** The count of how many times a key (term) occurs in the
    * universe (collection).
    * Underlying class is Long.
    */
  implicit class CollFreq(val underlying: Long) extends AnyVal  {
    def +(i: Int): Long = underlying + i
    def +(l: Long): Long = underlying + l
    def +(d: Double): Double = underlying + d
    def *(i: Int): Long = underlying * i
    def *(l: Long): Long = underlying * l
    def *(d: Double): Double = underlying * d
  }
  implicit def cf2long(cf: CollFreq): Long = cf.underlying


  /** Value for the maximum count of a particular count op.
    */
  implicit class MaximumCount(val underlying: Int) extends AnyVal
  implicit def maxcount2int(mc: MaximumCount): Int = mc.underlying
  implicit def maxcount2count(mc: MaximumCount): Count =
    new Count(mc.underlying)
  implicit def maxcount2len(mc: MaximumCount): Length =
    new Length(mc.underlying)

  /** The number of times a key (term) occurs in a particular target (doc).
    * Underlying class is Int.
    */
  implicit class Count(val underlying: Int) extends AnyVal  {
    def +(i: Int): Int = underlying + i
    def +(l: Long): Long = underlying + l
    def +(d: Double): Double = underlying + d
    def *(i: Int): Int = underlying * i
    def *(l: Long): Long = underlying * l
    def *(d: Double): Double = underlying * d
    def /(l: Length): Double = underlying.toDouble / l.underlying
  }

  /**
    * Value for a document identifier.
    * Underlying class is an Int.
    */
  implicit class Docid(val underlying: Int) extends AnyVal
  implicit object DocidOrder extends Ordering[Docid] {
    def compare(a: Docid, b: Docid) = a.underlying compare b.underlying
  }

  /** The size of the collection.
    * Underlying class is Long.
    */
  implicit class CollLength(val underlying: Long) extends AnyVal {
    def /(nd: NumDocs): Double = underlying.toDouble / nd.underlying
  }

  /** The length of any retrievable item.
    * Underlying value is Int.
    */
  implicit class Length(val underlying: Int) extends AnyVal  {
    def +(i: Int): Int = underlying + i
    def +(l: Long): Long = underlying + l
    def +(d: Double): Double = underlying + d
    def *(i: Int): Int = underlying * i
    def *(l: Long): Long = underlying * l
    def *(d: Double): Double = underlying * d
  }

  import scala.util.matching.Regex
  /** Implicit extension to the Regex class (done via composition)
  * this class provides easy "match" and "miss" type methods against
  * regular Strings. Only restriction is that the RichRegex must come
  * first in the comparison. See examples in the methods below.
  */
  implicit class RichRegex(underlying: Regex) {
    // TODO: Add some memoization of the underlying patterns. Should be done
    //       in the object, I imagine, as they should be shared once compiled.

    /** Returns true if the given string matches this pattern. Example:
      *
      * """\d+""".r matches "90210"  => True
      */
    def matches(s: String): Boolean = underlying.pattern.matcher(s).matches

    /** @see #matches(s: String)
      */
    def ==(s: String): Boolean = matches(s)

    /** Logical negation of #matches(s: String).
      */
    def misses(s: String): Boolean = (matches(s) == false)

    /** @see #misses(s: String)
      */
    def !=(s: String): Boolean = misses(s)
  }

  implicit def docid2int(d: Docid): Int = d.underlying
  implicit def len2int(l: Length): Int = l.underlying
  implicit def cl2long(cl: CollLength) = cl.underlying

  /** Implicit lift of Regex to RichRegex. */
  implicit def regexToRichRegex(r: Regex) = new RichRegex(r)

  // Explicitly for the implicit below (get it?)
  import scala.collection.mutable.{ListBuffer,PriorityQueue}
  implicit def q2list[T](q: PriorityQueue[T]): List[T] = {
    val b = ListBuffer[T]()
    while (!q.isEmpty) { b += q.dequeue }
    b.toList
  }

  /** Provides an ordering for [[ScoredDocument]]s. */
  implicit object ScoredDocumentOrdering extends Ordering[ScoredDocument] {
    def compare(a: ScoredDocument, b: ScoredDocument) =
      // TODO: This works, but gross.
      b.score.underlying compare a.score.underlying
  }

  /** Provides an [[scala.math.Ordering]] for [[Gram]]s. */
  implicit object GramOrdering extends Ordering[Gram] {
    def compare(a: Gram, b: Gram) = {
      val result = b.score compare a.score
      if (result == 0) a.term compare b.term else result
    }
  }


  /** Type definitions, most of which are for aliasing in the
    * package.
    */

  // To bring the packages in scope...
  import org.lemurproject.galago.core.index._
  import org.lemurproject.galago.core.index.corpus._

  // Because the names are ridiculously long...
  type GIndex = org.lemurproject.galago.core.index.Index
  type GDoc = org.lemurproject.galago.core.parse.Document
  type GIterator = org.lemurproject.galago.core.index.Iterator
  type ARCA = AggregateReader.CollectionAggregateIterator
  type ARNA = AggregateReader.NodeAggregateIterator
  type NS = AggregateReader.NodeStatistics
  type CS = AggregateReader.CollectionStatistics
  type TEI = ExtentIterator
  type TCI = CountIterator
  type MLI = LengthsReader.LengthsIterator

  /** For debugging/timing purposes, until I can figure out a macro to
    * compile this out - At least moving the definition will be easy.
    */
  def time[R](label:String)(block: => R): R = {
    val t0 = System.currentTimeMillis
    val result = block
    val t1 = System.currentTimeMillis
    debugf("%s: %d ms\n", label, (t1-t0).toInt)  // this is a test macro
    result
  }

  /** For debugging. This one is elidable, meaning given the correct flag,
    * the compiler will remove the call and the bytecode for this function.
    */
  @elidable(elidable.FINEST) def debug(msg: String) = Console.err.println(msg)
}
