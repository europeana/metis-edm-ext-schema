package eu.europeana.metis.edm.ext.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This class contains additional pre-validation for RDF data in XML. This is currently necessary
 * for two reasons (both of which are meant to be resolved in the short-to-medium term):
 *
 * <ol>
 *   <li>
 *     The XML schema for EDM internal contains two non-RDF-compliant fields related to provenance
 *     <code>edm:wasGeneratedBy</code> and <code>edm:confidenceLevel</code>. They need to be
 *     stripped from the data before it can be validated in the generic way.
 *   </li>
 *   <li>
 *     The XSLT transformation (to EDM internal) cannot handle nested objects, even though from an
 *     RDF perspective they are fine (and semantically equivalent to top-level objects). For
 *     instance, the following is not supported by the XSLT, but fully valid in RDF:
 *     {@snippet lang = "XML":
 *     <dc:creator>
 *       <edm:Agent rdf:about="http://www.example.com/XXXXXX">
 *         <skos:prefLabel>TEST</skos:prefLabel>
 *       </edm:Agent>
 *     </dc:creator>
 *     }
 *   </li>
 * </ol>
 * <p>
 * This class implements a streamed parsing, and thus supports large data volumes.
 */
public class RdfXmlPreValidationUtils {

  private static final String EDM_NAMESPACE = "http://www.europeana.eu/schemas/edm/";

  private static final Set<String> TOP_LEVEL_ELEMENTS = Set.of(
      EDM_NAMESPACE + "ProvidedCHO",
      "http://www.openarchives.org/ore/terms/Aggregation",
      EDM_NAMESPACE + "WebResource",
      EDM_NAMESPACE + "Agent",
      "http://www.w3.org/2004/02/skos/core#Concept",
      EDM_NAMESPACE + "Place",
      EDM_NAMESPACE + "TimeSpan",
      "http://creativecommons.org/ns#License",
      "http://rdfs.org/sioc/services#Service");

  private RdfXmlPreValidationUtils() {
  }

  /**
   * <p>
   * Analyzes an XML file ahead of RDF validation. It validates the XML data against the
   * XML-specific rules. This code uses XML-specific parsing, as RDF parsing would break on the
   * non-RDF-compliant fields.
   * </p>
   * <p>
   * Note: the reading is executed asynchronously and the method does not block. Any reading errors
   * will be submitted to the <code>reportItemConsumer</code>, and processing will stop. Similarly,
   * if the caller wishes to stop reading, they can close the returned <code>InputStream</code>,
   * this will be noticed by the writing process which will then also stop (and a report item will
   * be submitted to the <code>reportItemConsumer</code>).
   * </p>
   *
   * @param xmlData              The XML to validate.
   * @param reportItemConsumer   A consumer for validation report items in case issues were found.
   * @param reportNestedElements Whether nested elements should be reported (i.e., submitted to
   *                             <code>reportItemConsumer</code>).
   * @return An input stream containing the normalized version of the XML file that is ready for RDF
   * validation. The caller is responsible for closing this input stream.
   */
  protected static InputStream normalizeAndPreValidateXmlData(InputStream xmlData,
      Consumer<ValidationReportItem> reportItemConsumer, boolean reportNestedElements) {
    final PipedInputStream result = new PipedInputStream();
    CompletableFuture.supplyAsync(() -> {
      try (final PipedOutputStream normalizedRecord = new PipedOutputStream(result)) {
        normalizeAndPreValidateXmlDataPrivate(xmlData, reportItemConsumer, normalizedRecord,
            reportNestedElements);
      } catch (IOException | XMLStreamException | RuntimeException e) {
        reportItemConsumer.accept(new ValidationReportItem(null, null, null,
            "Could not read the XML content: " + e.getMessage(), ValidationIssueSeverity.ERROR));
      }
      return null;
    });
    return result;
  }

  private static void normalizeAndPreValidateXmlDataPrivate(InputStream xmlData,
      Consumer<ValidationReportItem> reportItemConsumer, OutputStream output,
      boolean reportNestedElements) throws XMLStreamException {

    // Set up the input with the XML data as provided.
    final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    final XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(xmlData);

    // Set up the output for the normalized XML data.
    final XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(output);

    // Go by the events. Keep track of the element's level (depth). Note: if writing to the output
    // fails, the loop is interrupted and an exception is returned.
    final AtomicInteger currentDepth = new AtomicInteger(0);
    while (reader.hasNext()) {

      // Get the next event.
      final XMLEvent nextEvent = reader.nextEvent();
      if (nextEvent.isStartElement()) {

        // Check whether we support this element at this depth.
        final StartElement element = nextEvent.asStartElement();
        if (currentDepth.get() > 1 && reportNestedElements) {
          verifyNestedElement(element, reportItemConsumer);
        }

        // Increment the depth counter.
        currentDepth.getAndIncrement();

        // Pass an adjusted start element, without the provenance attributes.
        writer.add(eventFactory.createStartElement(
            element.getName().getPrefix(), element.getName().getNamespaceURI(),
            element.getName().getLocalPart(), stripProvenanceAttributes(element.getAttributes()),
            element.getNamespaces(), element.getNamespaceContext()));

      } else {

        // Reduce the depth counter if needed.
        if (nextEvent.isEndElement()) {
          currentDepth.decrementAndGet();
        }

        // Pass on the event unchanged.
        writer.add(nextEvent);
      }
    }
  }

  private static Iterator<Attribute> stripProvenanceAttributes(Iterator<Attribute> attributes) {
    final Iterable<Attribute> attributesIterable = () -> Optional.ofNullable(attributes)
        .orElse(Collections.emptyIterator());
    return StreamSupport.stream(attributesIterable.spliterator(), false).filter(attribute -> {
      final QName name = attribute.getName();
      return !name.getNamespaceURI().equals(EDM_NAMESPACE) ||
          !Set.of("wasGeneratedBy", "confidenceLevel").contains(name.getLocalPart());
    }).iterator();
  }

  private static void verifyNestedElement(StartElement element,
      Consumer<ValidationReportItem> reportItemConsumer) {
    final String fullElementName =
        element.getName().getNamespaceURI() + element.getName().getLocalPart();
    final String elementId = Optional.ofNullable(element.getAttributeByName(
            new QName("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about")))
        .map(Attribute::getValue).orElse(null);
    if (TOP_LEVEL_ELEMENTS.contains(fullElementName)) {
      reportItemConsumer.accept(new ValidationReportItem(elementId, null, null,
          "XML Elements of type " + fullElementName
              + " must be top-level elements and may not be nested inside other elements.",
          ValidationIssueSeverity.ERROR));
    }
  }
}
