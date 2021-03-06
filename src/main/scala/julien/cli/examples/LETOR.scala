package julien
package cli
package examples

import java.io.PrintStream
import julien.retrieval._
import julien.retrieval.Utils._
import julien.retrieval.processor._

/** An example implementation of the features from
  * "LETOR: Benchmark Dataset for Research on Learning to Rank for Information
  * Retrieval" by Liu et al. (SIGIR 2007)
  *
  * Of course you need an index with the appropriate fields available.
  */
object LETOR extends Example {
  lazy val name: String = "letor"

  def checksOut(p: Parameters): Boolean =
    p.isString("query") &&
  p.isString("index") &&
  p.isList("fields")

  val help: String = """
Shows the "retrieval model" of features built around the LETOR feature set.
Each of the features are broken up into their respective categories:
Low-level features, High-level features, Hyperlink features, Hybrid features.
Features derived from the following paper:

"LETOR: Benchmark Dataset for Research on Learning to Rank for Information
Retrieval" by Liu et al. (SIGIR 2007)

Required parameters:

    query        string form of desired query
    index        location of an existing index

"""

  def run(params: Parameters, out: PrintStream): Unit = {
    implicit val index : Index = Index.disk(params.getString("index"))

    val queryTerms = params.getString("query").split(" ")
    val fields = params.getList("fields").asInstanceOf[List[String]]

    val modelFeatures = List.newBuilder[Feature]
    // F1: BM25 of the query terms
    modelFeatures += Sum(queryTerms.map {
      a => BM25(Term(a), IndexLengths())
    })

    // F2-F5: doc length of each field
    modelFeatures ++= fields.map { f =>
      val lView = IndexLengths(f)
      // Turn it into a feature
      new ScalarWeightedFeature with ChildlessOp {
        def eval(id: Int): Double = lView.length(id).toDouble
        lazy val views: Set[View] = Set[View](lView)
      }
    }

    // F6: HITS authority
    //modelFeatures += HITSAuthority()
    // F7: HITS hub
    //modelFeatures += HITSHub()
    // F8: HostRank?
    //modelFeatures += HostRank()

    // F9-F12 - summed IDF of query terms, in each field
    // We don't calculate it right now b/c we're not attached
    // to an index yet.
    for (f <- fields) {
      modelFeatures += Sum(queryTerms.map(q => IDF(Term(q, f))))
    }

    // F13: Sitemap score
    //modelFeatures += SitemapScore()

    // F14: PageRank
    //modelFeatures += PageRank()

    // Different smoothing methods over fields
    // These cover F15 thru F26
    // Note we don't have a section for "extracted title"
    // since it's a synthetic field.
    // TODO: Support for synthetic fields
    for (f <- fields) {
      val l = IndexLengths(f)
      modelFeatures += Sum(queryTerms.map { a => BM25(Term(a, f), l) })
      modelFeatures += Sum(queryTerms.map { a => Dirichlet(Term(a, f), l) })
      modelFeatures +=
        Sum(queryTerms.map { a => JelinekMercer(Term(a, f), l) })
      modelFeatures += Sum(queryTerms.map { a =>
        AbsoluteDiscount(Term(a, f), l, DocumentView())
      })
    }

    // F27: Sitemap feature
    //modelFeatures += SitemapFeature()

    // F28-F31: summed tf of each field
    for (f <- fields) {
      modelFeatures += Sum(queryTerms.map { q =>
        TF(Term(q, f), IndexLengths(f))
      })
    }

    // F32-F35: summed tf*idf of each field
    for (f <- fields) {
      modelFeatures += Sum(queryTerms.map { q =>
        TFIDF(Term(q, f), IndexLengths(f))
      })
    }

    // F36 - F38: Topical PageRank and HITS scores

    // F39 - F44: Hyperlink-based features

    // Execute it
    val model = modelFeatures.result
    val results = QueryProcessor(Sum(model))
    printResults(results, index, out)
  }
}
