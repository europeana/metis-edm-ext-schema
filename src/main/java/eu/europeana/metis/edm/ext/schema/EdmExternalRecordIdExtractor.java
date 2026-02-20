package eu.europeana.metis.edm.ext.schema;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.system.StreamRDFBase;
import org.apache.jena.sparql.core.Quad;

/**
 * This class provides functionality for extracting the record IDs from records. Specifically,
 * it returns all IRIs of resources of type <code>edm:ProvidedCHO</code> that are found in the
 * input data. This class implements a streamed parsing, and thus supports large data volumes.
 */
public class EdmExternalRecordIdExtractor extends DataWithDefaultBaseUrlHandler {

  private EdmExternalRecordIdExtractor() {
  }

  /**
   * Returns all record IDs.
   *
   * @param data The data.
   * @return A set with all declared record IDs. Is not null, but could be empty.
   * @throws DataParseException When the data could not be parsed.
   */
  public static Set<String> extractRecordIds(String data, Representation representation)
      throws DataParseException {
    return extractRecordIds(new ByteArrayInputStream(data.getBytes()), representation);
  }

  /**
   * Returns all record IDs.
   *
   * @param data The data.
   * @return A set with all declared record IDs. Is not null, but could be empty.
   * @throws DataParseException When the data could not be parsed.
   */
  public static Set<String> extractRecordIds(InputStream data, Representation representation)
      throws DataParseException {
    if (representation == Representation.XML) {

      // Read normalized XML data and extract IDs.
      final List<ValidationReportItem> preValidationItems = new ArrayList<>();
      final Set<String> result = new HashSet<>();
      try (InputStream normalizedData = RdfXmlPreValidationUtils.normalizeAndPreValidateXmlData(
          data, preValidationItems::add, false)) {
        result.addAll(extractRecordIdsFromNormalizedData(normalizedData, representation));
      } catch (IOException | RuntimeException e) {
        preValidationItems.add(new ValidationReportItem(null, null, null,
            "Could not parse input: " + e.getMessage(), ValidationIssueSeverity.ERROR));
      }

      // Check if there were serious issues and report them. Otherwise, return the encountered IDs.
      final Optional<ValidationReportItem> validationIssue = preValidationItems.stream()
          .filter(item -> item.severity() == ValidationIssueSeverity.ERROR).findFirst();
      if (validationIssue.isPresent()) {
        throw new DataParseException(validationIssue.get().message());
      }
      return result;

    } else {

      // No need to normalize - extract IDs directly.
      try {
        return extractRecordIdsFromNormalizedData(data, representation);
      } catch (RuntimeException e) {
        throw new DataParseException("Error trying to extract record IDs: " + e.getMessage(), e);
      }
    }
  }

  private static Set<String> extractRecordIdsFromNormalizedData(InputStream normalizedData,
      Representation representation) {
    final RDFParser parser = RDFParserBuilder.create().source(normalizedData)
        .lang(representation.getLang()).base(DEFAULT_BASE_URL).build();
    final Set<String> recordIds = new HashSet<>();
    parser.parse(new StreamRDFBase() {

      @Override
      public void triple(Triple triple) {
        final String pred = triple.getPredicate().isURI() ? triple.getPredicate().getURI() : null;
        final String obj = triple.getObject().isURI() ? triple.getObject().getURI() : null;
        if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(pred) &&
            "http://www.europeana.eu/schemas/edm/ProvidedCHO".equals(obj) &&
            triple.getSubject().isURI()
        ) {
          recordIds.add(normalizeUri(triple.getSubject().getURI()));
        }
      }

      @Override
      public void quad(Quad quad) {
        this.triple(quad.asTriple());
      }

    });
    return recordIds;
  }

  /**
   * Returns all record IDs.
   *
   * @param model The model containing the data.
   * @return A set with all declared record IDs. Is not null, but could be empty.
   */
  static Set<String> extractRecordIds(Model model) {

    // Check which resources are of type edm:ProvidedCHO. Note: subtypes are not supported.
    final String typeMapQuery = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX edm: <http://www.europeana.eu/schemas/edm/>
        SELECT DISTINCT ?resource
        WHERE {
            ?resource rdf:type edm:ProvidedCHO .
        }""";
    try (QueryExecution typeMapQueryExecution = QueryExecutionFactory
        .create(QueryFactory.create(typeMapQuery), model)) {
      final Set<String> recordIds = new HashSet<>();
      final ResultSet results = typeMapQueryExecution.execSelect();
      results.forEachRemaining(result -> {
        final RDFNode resource = result.get("resource");
        if (resource.isURIResource()) {
          recordIds.add(resource.asResource().getURI());
        }
      });
      return recordIds;
    }
  }
}
