package eu.europeana.metis.edm.ext.schema;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

import eu.europeana.metis.common.rdf.RdfRepresentation;
import eu.europeana.metis.edm.ext.schema.EdmExternalSegmenter.RecordConsumer.SegmentationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;

/**
 * This class can segment larger amounts of data into individual EDM-external records. A user can
 * create an instance and add data to it. When ready, the user can segment the data into individual
 * records.
 */
public class EdmExternalSegmenter implements AutoCloseable {

  public static final String EDM_NAMESPACE = "http://www.europeana.eu/schemas/edm/";

  public static final Resource EDM_PROVIDED_CHO = createResource(EDM_NAMESPACE + "ProvidedCHO");
  public static final Property EDM_AGGREGATED_CHO = createProperty(EDM_NAMESPACE, "aggregatedCHO");

  private final Model datasetModel;

  /**
   * Implementations of this interface can consume segmented records from the segmenter.
   *
   * @param <E> Exception type to throw.
   */
  public interface RecordConsumer<E extends Exception> {

    enum SegmentationResult {CONTINUE, TERMINATE}

    /**
     * Processes the data.
     *
     * @param record The record.
     * @return Whether to continue segmenting, or terminate the operation prematurely.
     * @throws E When there is an issue with processing the data.
     */
    SegmentationResult accept(WritableRecord record) throws E;
  }

  /**
   * Implementations of this interface are records that can be written to an output stream.
   */
  public interface WritableRecord {

    /**
     * @return The unique ID of this record.
     */
    String getRecordURI();

    /**
     * Write the record to an output stream.
     *
     * @param outputStream   The stream to write the record to.
     * @param representation The desired representation to write the output in.
     * @throws IOException When something goes wrong while writing to the stream.
     */
    void write(OutputStream outputStream, RdfRepresentation representation) throws IOException;
  }

  private record WritableRecordFromModel(Resource record) implements WritableRecord {

    @Override
    public String getRecordURI() {
      return record.getURI();
    }

    @Override
    public void write(OutputStream outputStream, RdfRepresentation representation)
        throws IOException {
      try {
        RDFDataMgr.write(outputStream, record.getModel(), representation.getLang());
      } catch (RuntimeException e) {
        throw new IOException("Could not write record.", e);
      }
    }
  }

  /**
   * Constructor. Creates an empty dataset to which data can be added before segmentation.
   */
  public EdmExternalSegmenter() {
    datasetModel = ModelFactory.createDefaultModel();
  }

  /**
   * Add data to this dataset.
   *
   * @param inputStream    The data.
   * @param representation The representation of the data.
   */
  public void addData(InputStream inputStream, RdfRepresentation representation) {
    // TODO be smarter about the base? Plan: use the same code as in RdfConversion. If no
    //  base is given, detect the default one in all statements we are copying. If there is an
    //  issue, we throw an exception.
    RDFDataMgr.read(datasetModel, inputStream, null, representation.getLang());
  }

  @Override
  public void close() {
    datasetModel.close();
  }

  /**
   * Count the number of records in the dataset.
   *
   * @return The number of records in the dataset.
   */
  public int countRecords() {
    final AtomicInteger counter = new AtomicInteger();
    consumeJenaIterator(datasetModel.listResourcesWithProperty(RDF.type, EDM_PROVIDED_CHO),
        providedCHO -> counter.incrementAndGet());
    return counter.get();
  }

  /**
   * Segment the dataset into individual records.
   *
   * @param consumer             A consumer for the individual records.
   * @param <E>                  The exception that may be thrown by the record consumer.
   * @throws E When something went wrong while consuming a record.
   */
  public <E extends Exception> void segment(RecordConsumer<E> consumer) throws E {

    // Iterate over the ProvidedCHO resources to extract the records.
    final ResIterator iterator = datasetModel.listResourcesWithProperty(RDF.type, EDM_PROVIDED_CHO);
    try {
      while (iterator.hasNext()) {
        final SegmentationResult writeResult = writeSingleRecord(iterator.next(), consumer);
        if (writeResult == SegmentationResult.TERMINATE) {
          break;
        }
      }
    } finally {
      iterator.close();
    }
  }

  /**
   * Writes a single record to the record consumer. The record is identified by a resource (the
   * <code>edm:ProvidedCHO</code>). This method will extract the record from the database model
   * and send it to the consumer.
   *
   * @param providedCHO          The record to extract and write.
   * @param consumer             The consumer that will accept the record.
   * @param <E>                  The exception that may be thrown by the record consumer.
   * @return The result of the consume action
   * @throws E When something went wrong while consuming the record.
   */
  private <E extends Exception> SegmentationResult writeSingleRecord(Resource providedCHO,
      RecordConsumer<E> consumer) throws E {
    final Model recordModel = extractRecordToNewModel(providedCHO);
    try {
      final Resource record = recordModel.getResource(providedCHO.getURI());
      return consumer.accept(new WritableRecordFromModel(record));
    } finally {
      recordModel.close();
    }
  }

  /**
   * <p>Extracts a record a new model. This record is identified by a resource (the
   * <code>edm:ProvidedCHO</code>). This method will crawl the dataset model for all resources that
   * belong to this record and add them to the new model. A record is defined as all content
   * reachable from this <code>edm:ProvidedCHO</code> as well as from any
   * <code>ore:Aggregation</code> that belongs to it. Care will be taken not to include other
   * records (they will be referenced by IRI).
   * </p>
   * <p>Implementation note: We considered implementing this by a SPARQL query that would get
   * all reachable content in one go. This does not perform well. It seems to be that the issue is
   * that the query needs to be parsed, built and planned for each record, whereas using the
   * built-in methods for the model (e.g., <code>model.listResourcesWithProperty</code>) is fully
   * optimized and performant.
   * </p>
   *
   * @param providedCHO The record to extract.
   * @return The new model.
   */
  private Model extractRecordToNewModel(Resource providedCHO) {

    // Create a model for the record.
    final Model recordModel = ModelFactory.createDefaultModel();
    recordModel.setNsPrefixes(datasetModel.getNsPrefixMap());

    // Prepare copying: queue for processing and set of handled resources to avoid cycles.
    final Set<Resource> handled = new HashSet<>();
    final ArrayDeque<Resource> queue = new ArrayDeque<>();
    queue.add(providedCHO);
    consumeJenaIterator(datasetModel.listResourcesWithProperty(EDM_AGGREGATED_CHO, providedCHO),
        queue::add);

    // Perform the copying.
    while (!queue.isEmpty()) {
      final Resource resource = queue.removeFirst();
      if (!handled.add(resource)) {
        continue;
      }
      copyResourceAndAddLinkedResourcesToQueue(resource, queue::addLast, recordModel);
    }

    // Done.
    return recordModel;
  }

  /**
   * Copy the resource (i.e., all triples with this resource as the subject) to the target model. If
   * we find linked resources (as the object of one of the triples), add them to the queue for
   * further processing. Note: a linked resource is not added if it has type edm:ProvidedCHO to
   * avoid crossing over into other records.
   *
   * @param resource      The resource to copy.
   * @param queueAppender The operation of adding any linked resources to the queue.
   * @param target        The model to which the resource is to be copied.
   */
  private void copyResourceAndAddLinkedResourcesToQueue(Resource resource,
      Consumer<Resource> queueAppender, Model target) {
    consumeJenaIterator(resource.listProperties(), statement -> {
      target.add(statement);
      final RDFNode object = statement.getObject();
      if (object.isResource() && !object.asResource().hasProperty(RDF.type, EDM_PROVIDED_CHO)) {
        queueAppender.accept(object.asResource());
      }
    });
  }

  /**
   * Convenience method that can consume the content of a Jena iterator and then close it
   * afterward.
   *
   * @param iterator The iterator to consume.
   * @param consumer The action to perform on iterator elements.
   * @param <T>      The type of the elements in the iterator.
   */
  private <T> void consumeJenaIterator(ExtendedIterator<T> iterator, Consumer<T> consumer) {
    try {
      iterator.forEachRemaining(consumer);
    } finally {
      iterator.close();
    }
  }
}
