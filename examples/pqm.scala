import edu.stanford.nlp.ie.AbstractSequenceClassifier
import edu.stanford.nlp.ie.crf._
import edu.stanford.nlp.io.IOUtils
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation
import scala.collection.mutable.PriorityQueue
import org.lemurproject.galago.core.parse.{Document,Tag,TagTokenizer}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.LinkedHashMap
import scala.collection.mutable.HashMap
import scala.collection.JavaConversions._
import scala.collection.immutable.Set
import scala.collection.Map

import java.net.URL
import scala.io.Source._
import java.io.IOException

object PQMFeatures {
  // components of our feature functions
  // Hold external references to the feature weights so we can tune quickly
  val weightTable = HashMap[String, List[Double]]()
  val tokenizer = new TagTokenizer()
  val stopwords = Stopwords.inquery

  // Define our feature function as a list of functions generated by this function
  def generate(
    qN: Node,
    nodeMap: Map[Node, java.lang.Object],
    mainIndex: Index,
    auxIndex: Index) : List[FeatureFunction] = {
    val feats = ListBuffer[FeatureFunction]()

    // Calculate these once since they're static
    val dummy = new Parameters
    val term = qN.getDefaultParameter
    val auxLengths = auxIndex.getLengthsIterator
    val auxSize = collectionLength(auxLengths)
    val auxNumDocs = numDocuments(auxLengths)

    // First one is to look up the cf in the title section of
    // the auxiliary index
    var f = () => {
      val node = formatNode(term, field = "title")
      val cCount = collectionCount(node, auxIndex)
      cCount.toDouble / auxSize
    }
    feats += f

    // Now do one for idf
    f = () => {
      val node = formatNode(term, field = "title")
      val dCount = documentCount(node, auxIndex)
      scala.math.log(auxNumDocs.toDouble / (dCount + 0.5))
    }
    feats += f

    // Time to get weird - every time the word occurs more than 10 times
    // in a doc, pull up the document, and determine the number of times
    // it's capitalized IN the raw text
    f = () => {
      // Grab the iterator for this feature and the context
      val it = nodeMap(qN).asInstanceOf[TCI]
      val count = it.count
      val id = it.getContext.document
      if (count > 10) {
        val doc = mainIndex.getDocument(mainIndex.getName(id), dummy)
        val matchPattern = String.format("""(?iu)\W%s\W""", term).r
        val total = matchPattern.findAllIn(doc.text).map { data =>
          if (data.size > 1 && data(0).isUpper) 1 else 0
        }.sum
        total
      } else {
        0.1
      }
    }
    feats += f

    // Finally - even worse - if the word occurs more than 20 times in a doc,
    // pull the doc, get all the terms in the title tag. Use those to
    // ping Google and look at the results page. Return the proportion of words
    // that are not stopwords
    f = () => {
      val it = nodeMap(qN).asInstanceOf[TCI]
      val count = it.count
      val id = it.getContext.document
      var returnValue = 0.0001
      if (count > 10) {
        Console.printf("F4: %s\n", term)
        val doc = mainIndex.getDocument(mainIndex.getName(id), dummy)
        if (doc.terms == null || doc.terms.size == 0) tokenizer.tokenize(doc)
        // Get the title tag and grab the content from the term vector
        val titleQuery = doc.tags.find(_.name.toLowerCase == "title") match {
          case None => ""
          case Some(t: Tag) =>
            doc.terms.slice(t.begin, t.end).toSet.mkString("+")
        }
        Console.printf("|TQ| = %d\n", titleQuery.size)

        if (titleQuery.size > 0) {
          // Now let's hit up Bing (Google's a pain in the ass to request from)
          val url =
            new URL(String.format("http://www.bing.com/?q=%s", titleQuery))
          val urlCon = url.openConnection()
          urlCon.setConnectTimeout(2000)
          urlCon.setReadTimeout( 2000 )
          val content =
            fromInputStream( urlCon.getInputStream ).getLines.mkString("\n")

          Console.printf("Retrieved URL '%s' with size: %d\n",
            url.toString, content.size)

          // Assuming things haven't gone south by here
          // let's extract some content (stupidly)
          val linkPat = """(?siu)<li><a (.*?)>(.*?)</a>""".r
          val linkMatches = linkPat.findAllIn(content).matchData
          // drop the first 8 and last 12 b/c they're boilerplate
          val linkData = linkMatches.toList.map(_.group(2)).drop(8).dropRight(12)
          val linkPhrases = linkData.map {
            l => """(?s)<.*?>""".r.replaceAllIn(l, "")
          }
          val linkTerms = linkPhrases.map(_.split(" ")).flatten.toSet
          val filtered = linkTerms.filterNot(t => stopwords(t.toLowerCase))
          returnValue = filtered.size.toDouble / linkTerms.size.toDouble
        }
      }
      returnValue
    }
    feats += f
    feats.toList
  }
}

object PQM extends App {
  // Query prep
  val graph = QueryGraph()
  graph.addIndex('aquaint)
  graph.addIndex('gov2)
  val queryNodes = args(0).map(Term(_, dirichlet))
  queryNodes.foreach(q => q.index = 'aquaint)
  for (n <- queryNodes) {
    val features = PQMFeatures.generate(n)
    val weights = List.fill(features.size)(1.0)
    graph.add(Node.features(n, features, weights))
  }

  val executor = ExecutionGraph(graph)
  val resultQueue = executor.run

  // Get doc names and print
  printResults(resultQueue, index)
}
