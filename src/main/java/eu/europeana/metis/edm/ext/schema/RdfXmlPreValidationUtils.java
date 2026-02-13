package eu.europeana.metis.edm.ext.schema;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
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
 * for two reasons:
 *
 * <ol>
 *   <li>
 *     The XML schema for EDM internal contains two non-RDF-compliant fields related to provenance
 *     <code>edm:wasGeneratedBy</code> and <code>edm:confidenceLevel</code>. They need to be
 *     stripped from the record before it can be validated in the generic way.
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
 *}
 *   </li>
 * </ol>
 * <p>
 * Note that the aim is for both of those issues to be resolved in the short-to-medium term.
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
   * <p>Analyzes an XML file ahead of RDF validation. It validates the XML record against the
   * XML-specific rules.
   * </p>
   * <p>
   * Note on implementation: this method returns a <code>String</code> so that it can be read by the
   * next step. It is conceivable to make this fully streamed (e.g., with a threaded approach using
   * a <code>PipedInputStream</code> - <code>PipedOutputStream</code> pair). This option was
   * rejected as we are specifically only handling one record (very little memory usage) and this
   * functionality should be temporary.
   * </p>
   *
   * @param xmlRecord          The XML to validate
   * @param reportItemConsumer A consumer for validation report items in case issues were found.
   * @return A normalized version of the XML file that is ready for RDF validation, or null if there
   * were issues parsing the XML (in which case a report item to that effect will have been sent to
   * <code>reportItemConsumer</code>).
   */
  protected static String normalizeAndPreValidateXmlRecord(String xmlRecord,
      Consumer<ValidationReportItem> reportItemConsumer) {
    try {
      return normalizeAndPreValidateXmlRecordPrivate(xmlRecord, reportItemConsumer);
    } catch (XMLStreamException e) {
      reportItemConsumer.accept(new ValidationReportItem(null, null, null,
          "Could not parse the XML content: " + e.getMessage(), ValidationIssueSeverity.ERROR));
      return null;
    }
  }

  private static String normalizeAndPreValidateXmlRecordPrivate(String xmlRecord,
      Consumer<ValidationReportItem> reportItemConsumer) throws XMLStreamException {

    // Set up the input with the XML record as provided.
    final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    final XMLEventReader reader = XMLInputFactory.newInstance()
        .createXMLEventReader(new ByteArrayInputStream(xmlRecord.getBytes()));

    // Set up the output for the normalized XML record.
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(output);

    // Go by the events. Keep track of the element's level (depth).
    final AtomicInteger currentDepth = new AtomicInteger(0);
    while (reader.hasNext()) {

      // Get the next event.
      final XMLEvent nextEvent = reader.nextEvent();
      if (nextEvent.isStartElement()) {

        // Check whether we support this element at this depth.
        final StartElement element = nextEvent.asStartElement();
        if (currentDepth.get() > 1) {
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

    // Done
    return output.toString();
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
