// BSD License (http://lemurproject.org/galago-license)
package org.lemurproject.galago.core.parse;

import gnu.trove.list.array.TIntArrayList;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import org.lemurproject.galago.core.types.NumberedField;
import org.lemurproject.galago.tupleflow.InputClass;
import org.lemurproject.galago.tupleflow.OutputClass;
import org.lemurproject.galago.tupleflow.StandardStep;
import org.lemurproject.galago.tupleflow.TupleFlowParameters;
import org.lemurproject.galago.tupleflow.Utility;
import org.lemurproject.galago.tupleflow.execution.Verified;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

/**
 * @author irmarc
 */
@InputClass(className = "org.lemurproject.galago.core.parse.Document")
@OutputClass(className = "org.lemurproject.galago.core.types.NumberedField")
@Verified
public class ContentEncoder extends StandardStep<Document, NumberedField> {

  HashSet<String> fieldsOfInterest;

  public ContentEncoder(TupleFlowParameters parameters) {
    fieldsOfInterest =
	new HashSet(parameters.getJSON().getAsList("field"));
  }

  @Override
  public void process(Document document) throws IOException {
      HashMap<String, List<List<String>>> entries =
	  new HashMap<String, List<List<String>>>();
      HashMap<String, TIntArrayList> positions = new
	  HashMap<String, TIntArrayList>();
      for (Tag t : document.tags) {
	  if (fieldsOfInterest.contains(t.name)) {
	      if (!entries.containsKey(t.name)) {
		  entries.put(t.name, new ArrayList<List<String>>());
		  positions.put(t.name, new TIntArrayList());
	      }
	      entries.get(t.name).add(document.terms.subList(t.begin, t.end));
	      positions.get(t.name).add(t.begin);
	  }
      }

      // pack stuff here - each entry in the hash map is a tuple to
      // process
      for (String fieldName : entries.keySet()) {
	  List<List<String>> snippets =
	      entries.get(fieldName);
	  TIntArrayList pos = positions.get(fieldName);

	  // Now rip it out to a byte array
	  ByteArrayOutputStream bytes = new ByteArrayOutputStream();
	  DataOutputStream output =
	      new DataOutputStream(new SnappyOutputStream(bytes));
	  // Write # of times this field happened
	  output.writeInt(snippets.size());
	  // Write the positions of the occurrences
	  ByteArrayOutputStream posBytes = new ByteArrayOutputStream();
	  DataOutputStream dos = new DataOutputStream(posBytes);
	  int lastPos = 0;
	  for (int i = 0; i < pos.size(); ++i) {
	      int loc = pos.get(i);
	      Utility.compressInt(dos, loc-lastPos);
	      lastPos = loc;
	  }
	  // Prefix w/ length in bytes to move as a block
	  dos.flush();
	  output.writeInt(posBytes.size());
	  output.write(posBytes.toByteArray());

	  // Write the occurrences themselves
	  ByteArrayOutputStream contentBytes = new ByteArrayOutputStream();
	  dos = new DataOutputStream(contentBytes);
	  for (int i = 0; i < snippets.size(); ++i) {
	      List<String> entry = snippets.get(i);
	      Utility.compressInt(dos, entry.size());
	      for (String word : entry) {
		  byte[] encoded = Utility.fromString(word);
		  Utility.compressInt(dos, encoded.length);
		  dos.write(encoded);
	      }
	  }
	  dos.flush();
	  output.writeInt(contentBytes.size());
	  output.write(contentBytes.toByteArray());

	  NumberedField nf =
	      new NumberedField(Utility.fromString(fieldName),
				(long) document.identifier,
				bytes.toByteArray());
	  processor.process(nf);
      }
  }
}