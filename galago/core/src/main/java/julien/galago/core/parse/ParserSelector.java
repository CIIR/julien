// BSD License (http://lemurproject.org/galago-license)
package julien.galago.core.parse;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.EOFException;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import julien.galago.core.types.DocumentSplit;
import julien.galago.tupleflow.Counter;
import julien.galago.tupleflow.InputClass;
import julien.galago.tupleflow.OutputClass;
import julien.galago.tupleflow.Parameters;
import julien.galago.tupleflow.StandardStep;
import julien.galago.tupleflow.StreamCreator;
import julien.galago.tupleflow.TupleFlowParameters;
import julien.galago.tupleflow.Utility;
import julien.galago.tupleflow.execution.Verified;

import org.tukaani.xz.XZInputStream;

/**
 * Determines the class type of the input split, either based
 * on the "filetype" parameter passed in, or by guessing based on
 * the file path extension.
 *
 * (7/29/2012, irmarc): Refactored to be plug-and-play. External filetypes
 * may be added via the parameters.
 *
 * Instantiation of a type-specific parser (TSP) is done by the UniversalParser.
 * It checks the formal argument types of the (TSP) to match on the possible
 * input methods it has available (i.e. an inputstream or a buffered reader over
 * the input data. Additionally, any TSP may have TupleFlowParameters in its
 * formal argument list, and the parameters provided to the UniversalParser will
 * be forwarded to the TSP instance.
 *
 * @author trevor, sjh, irmarc
 */
@Verified
@InputClass(className = "julien.galago.core.types.DocumentSplit")
@OutputClass(className = "julien.galago.core.parse.Document")
public class ParserSelector extends StandardStep<DocumentSplit, Document> {

    // The built-in type map
    static String[][] sFileTypeLookup = {
	{"html", FileParser.class.getName()},
	{"xml", FileParser.class.getName()},
	{"txt", FileParser.class.getName()},
	{"arc", ArcParser.class.getName()},
	{"warc", WARCParser.class.getName()},
	{"trectext", TrecTextParser.class.getName()},
	{"trecweb", TrecWebParser.class.getName()},
	{"twitter", TwitterParser.class.getName()},
	{"wiki", WikiParser.class.getName()},
	{"wikiwex", WikiWexParser.class.getName()}
    };

  private HashMap<String, Class> fileTypeMap;
  private Counter documentCounter;
  private TupleFlowParameters tfParameters;
  private Parameters parameters;
  private Logger LOG = Logger.getLogger(getClass().toString());
  private Closeable source;
  private byte[] subCollCheck = "subcoll".getBytes();

  public ParserSelector() {
    this(new Parameters());
  }

  public ParserSelector(Parameters p) {
    this.tfParameters = null;
    this.parameters = p;
    buildFileTypeMap();
  }

  public ParserSelector(TupleFlowParameters parameters) {
    this.tfParameters = parameters;
    documentCounter = parameters.getCounter("Documents Parsed");
    this.parameters = parameters.getJSON();
    buildFileTypeMap();
  }

  private void buildFileTypeMap() {
      try {
	  fileTypeMap = new HashMap<String, Class>();
	  for (String[] mapping : sFileTypeLookup) {
	      fileTypeMap.put(mapping[0], Class.forName(mapping[1]));
	  }

	  // Look for external mapping definitions
	  if (parameters.containsKey("externalParsers")) {
	      List<Parameters> externalParsers =
		  (List<Parameters>) parameters.getAsList("externalParsers");
	      for (Parameters extP : externalParsers) {
		  fileTypeMap.put(extP.getString("filetype"),
				  Class.forName(extP.getString("class")));
	      }
	  }
      } catch (ClassNotFoundException cnfe) {
	  throw new IllegalArgumentException(cnfe);
      }
  }

  @Override
  public void process(DocumentSplit split) throws IOException {
    DocumentStreamParser parser = null;
    int count = 0;
    long limit = Long.MAX_VALUE;
    int offset = split.startDocument;

    // Determine the file type either from the parameters
    // or from the guess in the splits
    String fileType;
    if (parameters.containsKey("filetype")) {
      fileType = parameters.getString("filetype");
    } else {
      fileType = split.fileType;
    }

    if (fileTypeMap.containsKey(fileType)) {
	try {
	    parser = constructParserWithSplit(fileTypeMap.get(fileType), split);
	} catch (EOFException ee) {
	    System.err.printf("Found empty split %s. Skipping due to no content.", split.toString());
	    return;
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
    } else {
        throw new IOException("Unknown fileType: " + fileType
			      + " for fileName: " + split.fileName);
    }

    // A parser is instantiated. Start producing documents for consumption
    // downstream.
    Document document;
    while ((document = parser.nextDocument()) != null) {
      // Numbering based on the offset + # seen in this split
      document.identifier = offset + count;
      processor.process(document);
      if (documentCounter != null) {
        documentCounter.increment();
      }
      count++;

      // Enforces limitations imposed by the endKey subcollection specifier.
      // See DocumentSource for details.
      if (count >= limit) {
        break;
      }

      if (count % 10000 == 0) {
    	  Logger.getLogger(getClass().toString()).log(Level.WARNING, "Read " + count + " from split: " + split.fileName);
      }
    }

    if (parser != null) {
	parser.close();
    }
  }

  // Try like Hell to match up the formal parameter list with the available
  // objects/methods in this class.
  //
  // Longest constructor is built first.
  private DocumentStreamParser constructParserWithSplit(Class parserClass,
							DocumentSplit split)
      throws IOException, InstantiationException, IllegalAccessException,
      InvocationTargetException {
      Constructor[] constructors = parserClass.getConstructors();
      Arrays.sort(constructors, new Comparator<Constructor>() {
              @Override
	      public int compare(Constructor c1, Constructor c2) {
		  return (c2.getParameterTypes().length -
			  c1.getParameterTypes().length);
	      }
	  });
      Class[] formals;
      ArrayList<Object> actuals;
      for (Constructor constructor : constructors) {
	  formals = constructor.getParameterTypes();
	  actuals = new ArrayList<Object>(formals.length);
	  for (Class formalClass : formals) {
	      if (formalClass.isAssignableFrom(BufferedInputStream.class)) {
		  actuals.add(getLocalBufferedInputStream(split));
	      } else if (formalClass.isAssignableFrom(BufferedReader.class)) {
		  actuals.add(getLocalBufferedReader(split));
	      } else if (String.class.isAssignableFrom(formalClass)) {
		  actuals.add(split.fileName);
	      } else if (DocumentSplit.class.isAssignableFrom(formalClass)) {
		  actuals.add(split);
	      } else if (Parameters.class.isAssignableFrom(formalClass)) {
		  actuals.add(parameters);
	      } else if (TupleFlowParameters.class.isAssignableFrom(formalClass)) {
		  actuals.add(tfParameters);
	      }
	  }
	  if (actuals.size() == formals.length) {
	      return (DocumentStreamParser)
		  constructor.newInstance(actuals.toArray(new Object[0]));
	  }
      }
      // None of the constructors worked. Complain.
      StringBuilder builder = new StringBuilder();
      builder.append("No viable constructor for file type parser ");
      builder.append(parserClass.getName()).append("\n\n");
      builder.append("Valid formal parameters include TupleFlowParameters,");
      builder.append(" Parameters, BufferedInputStream or BufferedReader,\n");
      builder.append(" String (fileName is passed as the actual), or");
      builder.append(" DocumentSplit.\n\nConstuctors found:\n");
      for (Constructor c : constructors) {
	  builder.append("( ");
	  formals = c.getParameterTypes();
	  for (Class klazz : formals) {
	      builder.append(klazz.getName()).append(",");
	  }
	  builder.append(")\n");
      }
      throw new IllegalArgumentException(builder.toString());
  }

  public static boolean isParsable(String extension) {
      for (String[] entry : sFileTypeLookup) {
	  if (entry[0].equals(extension)) {
	      return true;
	  }
      }
      return false;
  }

  public BufferedReader getLocalBufferedReader(DocumentSplit split) throws IOException {
    BufferedReader br = getBufferedReader(split);
    source = br;
    return br;
  }

  public static BufferedReader getBufferedReader(String filename, boolean isCompressed) throws IOException {
    FileInputStream stream = StreamCreator.realInputStream(filename);
    BufferedReader reader;

    if (isCompressed) {
      // Determine compression type
      if (filename.endsWith("gz")) { // Gzip
        reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(stream)));
      } else { // BZip2
        BufferedInputStream bis = new BufferedInputStream(stream);
        //bzipHeaderCheck(bis);
        reader = new BufferedReader(new InputStreamReader(new BZip2CompressorInputStream(bis)));
      }
    } else {
      reader = new BufferedReader(new InputStreamReader(stream));
    }
    return reader;
  }

  public static BufferedReader getBufferedReader(DocumentSplit split) throws IOException {
    FileInputStream stream = StreamCreator.realInputStream(split.fileName);
    BufferedReader reader;

    if (split.isCompressed) {
      // Determine compression type
      if (split.fileName.endsWith("gz")) { // Gzip
        reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(stream)));
      } else { // BZip2
        BufferedInputStream bis = new BufferedInputStream(stream);
        //bzipHeaderCheck(bis);
        reader = new BufferedReader(new InputStreamReader(new BZip2CompressorInputStream(bis)));
      }
    } else {
      reader = new BufferedReader(new InputStreamReader(stream));
    }
    return reader;
  }

  public BufferedInputStream getLocalBufferedInputStream(DocumentSplit split) throws IOException {
    BufferedInputStream bis = getBufferedInputStream(split);
    source = bis;
    return bis;
  }

  public static BufferedInputStream getBufferedInputStream(DocumentSplit split) throws IOException {
    FileInputStream fileStream = StreamCreator.realInputStream(split.fileName);
    BufferedInputStream stream;

    if (split.isCompressed) {
      // Determine compression algorithm
      if (split.fileName.endsWith("gz")) { // Gzip
        stream = new BufferedInputStream(new GZIPInputStream(fileStream));
      } else if (split.fileName.endsWith("xz")) {
          stream = new BufferedInputStream(new XZInputStream(fileStream), 10*1024);
      } else { // bzip2
        BufferedInputStream bis = new BufferedInputStream(fileStream);
        //bzipHeaderCheck(bis);
        stream = new BufferedInputStream(new BZip2CompressorInputStream(bis));
      }
    } else {
      stream = new BufferedInputStream(fileStream);
    }
    return stream;
  }
}