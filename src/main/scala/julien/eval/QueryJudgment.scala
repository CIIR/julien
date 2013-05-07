package julien
package eval

import gnu.trove.map.hash.TObjectIntHashMap
import scala.collection.Iterable

case class Judgment(var name: String, var label: Int)

class QueryJudgment(val queryName: String) extends Iterable[Judgment] {
  private var _numRel = 0
  private var _numNonRel = 0
  private var numUnknown = 0
  val judgments = new TObjectIntHashMap[String]()

  def +=(docName: String, label: Int): this.type = {
    assume(!judgments.containsKey(docName), s"Got $docName already.")
    judgments.put(docName, label)
    // TODO : Not sure I like this binarization
    if (label > 0)
      _numRel += 1
    else
      _numNonRel += 1
    this
  }

  def numRel(): Int = _numRel
  def numNonRel(): Int = _numNonRel
  def isJudged(name: String): Boolean = judgments.containsKey(name)
  def isNonRelevant(name: String): Boolean = !isRelevant(name)
  def contains(name: String): Boolean = judgments.containsKey(name)
  def isRelevant(name: String): Boolean =
    judgments.containsKey(name) && judgments.get(name) > 0
  // TODO: Fast-fail this? I think so, but?
  def apply(name: String): Int = {
    assume(judgments.containsKey(name), s"$name is unjudged")
    judgments.get(name)
  }

  def getOrElse(name: String, default: Int): Int =
    if (judgments.containsKey(name)) judgments.get(name) else default

  def keys: Array[String] = judgments.keys.asInstanceOf[Array[String]]
  def values: Array[Int] = judgments.values
  override def size: Int = judgments.size
  def iterator: Iterator[Judgment] = new Iterator[Judgment] {
    val j = Judgment("", 0)
    var idx = 0
    val k = keys
    def hasNext: Boolean = idx < keys.length-1
    def next: Judgment = {
      j.name = keys(idx)
      j.label = judgments.get(j.name)
      idx += 1
      j
    }
  }
}