// BSD License (http://lemurproject.org/galago-license)
package julien.galago.core.parse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import julien.galago.core.parse.DocumentSource;
import julien.galago.core.types.DocumentSplit;
import julien.galago.tupleflow.FakeParameters;
import julien.galago.tupleflow.Parameters;
import julien.galago.tupleflow.Processor;
import julien.galago.tupleflow.Utility;
import junit.framework.TestCase;


/**
 *
 * @author trevor
 */
public class DocumentSourceTest extends TestCase {

  public DocumentSourceTest(String testName) {
    super(testName);
  }

  public class FakeProcessor implements Processor<DocumentSplit> {

    public ArrayList<DocumentSplit> splits = new ArrayList<DocumentSplit>();

    public void process(DocumentSplit split) {
      splits.add(split);
    }

    public void close() throws IOException {
    }
  }

  public void testUnknownFile() throws Exception {
    Parameters p = new Parameters();
    p.set("filename", "foo.c");
    DocumentSource source = new DocumentSource(new FakeParameters(p));
    FakeProcessor processor = new FakeProcessor();
    source.setProcessor(processor);

    boolean threwException = false;
    try {
      source.run();
    } catch (Exception e) {
      threwException = true;
    }
    assertTrue(threwException);
  }

  public void testUnknownExtension() throws Exception {
    File tempFile = Utility.createTemporary();
    Parameters p = new Parameters();
    p.set("filename", tempFile.getAbsolutePath());
    p.set("inputPolicy", "warn");
    DocumentSource source = new DocumentSource(new FakeParameters(p));
    FakeProcessor processor = new FakeProcessor();
    source.setProcessor(processor);

    source.run();
    assertEquals(0, processor.splits.size());
    tempFile.delete();
  }
}