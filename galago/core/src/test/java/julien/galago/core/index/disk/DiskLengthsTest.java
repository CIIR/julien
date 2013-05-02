/*
 *  BSD License (http://lemurproject.org/galago-license)
 */
package julien.galago.core.index.disk;

import java.io.File;
import java.io.IOException;

import julien.galago.core.index.disk.DiskLengthsReader;
import julien.galago.core.index.disk.DiskLengthsWriter;
import julien.galago.core.index.disk.DiskLengthsReader.KeyIterator;
import julien.galago.core.index.disk.DiskLengthsReader.StreamLengthsIterator;
import julien.galago.core.types.FieldLengthData;
import julien.galago.tupleflow.FakeParameters;
import julien.galago.tupleflow.Parameters;
import julien.galago.tupleflow.Utility;
import junit.framework.TestCase;


/**
 *
 * @author sjh
 */
public class DiskLengthsTest extends TestCase {

  public DiskLengthsTest(String name) {
    super(name);
  }

  public void testLengths() throws IOException {
    File len = null;
    try {

      len = Utility.createTemporary();

      Parameters p = new Parameters();
      p.set("filename", len.getAbsolutePath());
      DiskLengthsWriter writer = new DiskLengthsWriter(new FakeParameters(p));

      byte[] key = Utility.fromString("all");
      for (int i = 10; i <= 100; i++) {
        writer.process(new FieldLengthData(key, i, i + 1));
      }

      writer.process(new FieldLengthData(key, 110, 111));

      writer.close();

      DiskLengthsReader reader = new DiskLengthsReader(len.getAbsolutePath());

      // first some random seeks
      assertEquals(91,reader.getLength(90));
      assertEquals(51, reader.getLength(50));
      assertEquals(0, reader.getLength(105));
      assertEquals(111, reader.getLength(110));

      KeyIterator ki = reader.keys();
      StreamLengthsIterator streamItr = ki.getStreamValueIterator();


      streamItr.syncTo(50);
      assertEquals(streamItr.currentCandidate(), 50);
      assertEquals(streamItr.getCurrentLength(), 51);

      streamItr.syncTo(90);
      assertEquals(streamItr.currentCandidate(), 90);
      assertEquals(streamItr.getCurrentLength(), 91);

      streamItr.syncTo(90);
      assertEquals(streamItr.currentCandidate(), 90);
      assertEquals(streamItr.getCurrentLength(), 91);

      streamItr.syncTo(110);
      assertEquals(streamItr.currentCandidate(), 110);
      assertEquals(streamItr.getCurrentLength(), 111);

      reader.close();


    } finally {
      if (len != null) {
        len.delete();
      }

    }
  }
}