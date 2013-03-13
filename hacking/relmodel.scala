import org.lemurproject.galago.core.index.disk.DiskIndex
import org.lemurproject.galago.core.retrieval.iterator.MovableLengthsIterator
import org.lemurproject.galago.core.retrieval.query.{StructuredQuery, Node}
import org.lemurproject.galago.core.index.disk.PositionIndexReader
import org.lemurproject.galago.tupleflow.Utility
import org.lemurproject.galago.tupleflow.Parameters
import scala.collection.mutable.PriorityQueue
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.LinkedHashMap
import scala.collection.JavaConversions._

// Hack for now to load some common definitions
import GalagoBridging._

// Query prep
val query = "new york city"
val queryNodes = bowNodes(query)

val index = Sources.get('aquaint)
val lengths = index.getLengthsIterator
val nodeMap = LinkedHashMap[Node, java.lang.Object]()
for (n <- queryNodes) { nodeMap.update(n,
  index.getIterator(n).asInstanceOf[PositionIndexReader#TermCountIterator])
}

val iterators = nodeMap.values.toList.map(obj2movableIt(_))

// These are the LM scorers - will need them later
val lmScorers = unigrams(nodeMap, index, lengths)

// Scoring loop for first pass
var resultQueue = standardScoringLoop(lmScorers, iterators, lengths)
val fbDocs = 10  // make proper variable later

// Trickier part - set up for 2nd run

// take fbDocs
var initialResults = ListBuffer[ScoredDocument]()
while (initialResults.size < fbDocs && !resultQueue.isEmpty)
  initialResults += resultQueue.dequeue

// now recover "probabilities" of each doc
val max = initialResults.map(_.score).max
val logSumExp = max + scala.math.log(initialResults.map { v =>
 scala.math.exp(v.score - max)
}.sum)
initialResults = initialResults.map { sd =>
  ScoredDocument(sd.docid, scala.math.exp(sd.score - logSumExp))
}

// get the actual documents, and count the grams
import org.lemurproject.galago.core.parse.Document
import org.lemurproject.galago.core.parse.TagTokenizer
val tokenizer = new TagTokenizer()
val dummy = new Parameters()
// load and tokenize docs
val docs = initialResults.map { SD =>
  val d = index.getDocument(index.getName(SD.docid), dummy)
  if (d.terms == null || d.terms.size == 0) tokenizer.tokenize(d)
  d // tokenize method returns void/Unit
}

// Get stopwords to filter
val stopwords = Stopwords.inquery

// histograms of the # of occurrences - each doc is a histogram
val hists = docs.map( d => (d.identifier, histogram(d)) ).toMap

// Set of fb terms
var terms = hists.values map(_.counts.keySet) reduceLeft { (A,B) => A ++ B }
// that are NOT stopwords
terms = terms.filterNot(stopwords(_))
// that are NOT 1-character or less
terms = terms.filterNot(_.size <= 1)
// and are NOT all digits
terms = terms.filterNot(isAllDigits(_))

// Apparently we need lengths too. Makes (docid -> length) map
val doclengths = initialResults.map(_.docid).map {
  A => (A, index.getLength(A))
}.toMap

// Time to score the terms
val grams = terms.map { T =>
  // map to score-per-doc then sum
  val score = initialResults.map { SD =>
    val tf =
      hists(SD.docid).counts.getOrElse(T, 0).toDouble / doclengths(SD.docid)
    SD.score * tf
  }.sum
  Gram(T, score)
}

// Sort and keep top "fbTerms"
val fbTerms = 20
// selectedGrams = term -> Gram map
val selectedGrams =
  grams.toList.sorted(GramOrdering).take(fbTerms).map(g => (g.term, g)).toMap

// Need to open new iterators - only open the ones we don't already have
// rmNodes = term -> Node map
val rmNodes =
  selectedGrams.values.map { g =>
    (g.term, formatNode(g.term, "counts"))
  } filterNot { pair => nodeMap.contains(pair._2) }

val rmNodeMap = LinkedHashMap[Node, java.lang.Object]()
for ((term, node) <- rmNodes) { rmNodeMap.update(node,
  index.getIterator(node).asInstanceOf[PositionIndexReader#TermCountIterator])
}

val rmScorers = rmNodes.map { case (term, node) =>
    val scorer = dirichlet(collectionFrequency(node, index, lengths))
    val ps = ParameterizedScorer(scorer, lengths, rmNodeMap(node))
    ps.weight = selectedGrams(term).score
    ps
}.toList

val finalScorers = List(
  ParameterizedScorer(lmScorers, 0.7),
  ParameterizedScorer(rmScorers, 0.3)
)

val finalIterators = (rmNodeMap.values.toList.map(obj2movableIt(_)) ++
  iterators).toSet.toList
// Make sure everything's ready to go
finalIterators.foreach(_.reset)
lengths.reset

// Scoring loop
resultQueue = standardScoringLoop(finalScorers, finalIterators, lengths)

// Get doc names and print
printResults(resultQueue, index)
