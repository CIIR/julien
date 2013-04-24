package julien
package retrieval

import scala.io.Source
import org.lemurproject.galago.tupleflow.{Parameters,Utility}
import java.io.{File,InputStream,PrintStream,ByteArrayOutputStream}
import java.util.logging.{Level,Logger}
import org.scalatest._
import julien.cli.BuildIndex

class IndexingSpec extends FlatSpec with BeforeAndAfter {
  val tmpForInput: File =
    new File(Utility.createTemporary.getAbsolutePath + ".gz")
  val tmpForIndex: File = Utility.createTemporaryDirectory("tmpindex")

  // Extract our source docs, write to an index, and run some tests.
  before {
    // Turn off logging so the tests aren't diluted.
    Logger.getLogger("").setLevel(Level.OFF)

    // Extract resource
    val istream = getClass.getResourceAsStream("/wikisample.gz")
    assert (istream != null)
    Utility.copyStreamToFile(istream, tmpForInput)

    // Set some parameters
    val params = new Parameters
    params.set("inputPath", tmpForInput.getAbsolutePath)
    params.set("indexPath", tmpForIndex.getAbsolutePath)
    params.set("deleteJobDir", true)
    params.set("mode", "local")
    val parser = new Parameters
    parser.set("filetype", "wikiwex")
    params.set("parser", parser)
    params.set("corpus", true)
    val corpus = new Parameters
    val corpusFile =
      new File(tmpForIndex.getAbsolutePath, "corpus")
    corpusFile.mkdir
    corpus.set("filename", corpusFile.getAbsolutePath)
    params.set("corpusParameters", corpus)

    // Sort of a "/dev/null"
    val receiver = new PrintStream(new ByteArrayOutputStream)

    BuildIndex.run(params, receiver)
    receiver.close
  }

  // Clean up the small index
  after {
    tmpForInput.delete()
    Utility.deleteDirectory(tmpForIndex)
  }

  "A built index" should "have 77 documents" in {
    val index = Index.disk(tmpForIndex.getAbsolutePath)
    expect(77L) { index.numDocuments } // currently we have some loss
  }

  it should "have 11905 unique terms" in {
    val index = Index.disk(tmpForIndex.getAbsolutePath)
    expect(11905L) { index.vocabularySize }
  }

  it should "have 184629 term instances" in {
    val index = Index.disk(tmpForIndex.getAbsolutePath)
    expect(184629L) { index.collectionLength }
  }
}