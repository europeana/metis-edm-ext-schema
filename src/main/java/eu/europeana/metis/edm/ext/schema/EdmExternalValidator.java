package eu.europeana.metis.edm.ext.schema;

import eu.europeana.metis.common.rdf.RdfRepresentation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathVisitorBase;

/**
 * This class provides EDM external validation.
 */
public class EdmExternalValidator extends DataWithDefaultBaseUrlHandler {

  // Only access this through the synchronized getter.
  private static Shapes shapes = null;

  private static synchronized Shapes getShapes() {
    if (shapes == null) {
      final Model shapesModel = ModelFactory.createDefaultModel()
          .read("schema/edm_ext_shacl_shapes.ttl", Lang.TTL.getLabel());
      final Model enhancedShapeModel = ModelFactory
          .createInfModel(ReasonerRegistry.getOWLReasoner(), shapesModel);
      shapes = Shapes.parse(enhancedShapeModel);
    }
    return shapes;
  }

  private static String toString(Node node) {
    if (node == null || node.isBlank()) {
      return null;
    }
    if (node.isLiteral()) {
      return node.getLiteral().toString();
    }
    if (node.isURI()) {
      return normalizeUri(node.getURI());
    }
    return null;
  }

  private static String toString(Path path) {
    if (path == null) {
      return null;
    }
    final Set<String> foundPaths = new HashSet<>();
    path.visit(new PathVisitorBase() {
      @Override
      public void visit(P_Link pathNode) {
        foundPaths.add(EdmExternalValidator.toString(pathNode.getNode()));
      }
    });
    return foundPaths.size() == 1 ? foundPaths.iterator().next() : null;
  }

  /**
   * Validates a single record.
   *
   * @param record         The record to validate.
   * @param representation The representation of the record.
   * @return A report with found validation issues.
   */
  public ValidationReport validateSingleRecord(String record, RdfRepresentation representation) {
    return validateSingleRecord(new ByteArrayInputStream(record.getBytes()), representation);
  }

  /**
   * Validates a single record.
   *
   * @param record         The record to validate.
   * @param representation The representation of the record.
   * @return A report with found validation issues.
   */
  public ValidationReport validateSingleRecord(InputStream record, RdfRepresentation representation) {
    if (representation == RdfRepresentation.XML) {

      // If we have an XML record, we need to normalize and pre-validate first.
      final List<ValidationReportItem> preValidationItems = new ArrayList<>();
      try (InputStream normalizedRecord = RdfXmlPreValidationUtils.normalizeAndPreValidateXmlData(
          record, preValidationItems::add, true)) {
        final ValidationReport report = validateSingleNormalizedRecord(normalizedRecord,
            representation.getLang());
        return ValidationReport.merge(report, preValidationItems);
      } catch (IOException e) {
        preValidationItems.add(new ValidationReportItem(null, null, null,
            "Could not parse XML input: " + e.getMessage(), ValidationIssueSeverity.ERROR));
        return new ValidationReport(null, ValidationIssueSeverity.ERROR, preValidationItems);
      }
    } else {

      // No normalization needed: proceed as per usual.
      return validateSingleNormalizedRecord(record, representation.getLang());
    }
  }

  private ValidationReport validateSingleNormalizedRecord(InputStream record, Lang lang) {

    // Parse the model
    final Model model = ModelFactory.createDefaultModel();
    try {
      model.read(record, DEFAULT_BASE_URL, lang.getLabel());
    } catch (RuntimeException e) {
      return new ValidationReport(null, ValidationIssueSeverity.ERROR,
          List.of(new ValidationReportItem(null, null, null,
              "Could not parse input: " + e.getMessage(), ValidationIssueSeverity.ERROR)));
    }

    // Global analysis: parse and analyze the model as a whole.
    final Pair<String, ValidationReportItem> idCheck = checkForUniqueProvidedCHOId(model);
    final List<ValidationReportItem> unsupportedTypeCheck = checkForUnsupportedTypes(model);
    final List<ValidationReportItem> orphanedResourcesCheck = checkForOrphanedResources(model);

    // Local analysis: validate the provided shapes. First, add the resource hierarchy.
    SupportedResourceTypes.get().addTypeHierarchyToModel(model);
    final List<ValidationReportItem> localReportItems = new ArrayList<>();
    ShaclValidator.get().validate(getShapes(), model.getGraph()).getEntries().forEach(entry ->
        localReportItems.add(new ValidationReportItem(toString(entry.focusNode()),
            toString(entry.resultPath()), toString(entry.value()), entry.message(),
            ValidationIssueSeverity.forSeverity(entry.severity()))));

    // Compile and run report.
    final List<ValidationReportItem> allReportItems = new ArrayList<>();
    Optional.ofNullable(idCheck.getRight()).ifPresent(allReportItems::add);
    allReportItems.addAll(unsupportedTypeCheck);
    allReportItems.addAll(orphanedResourcesCheck);
    allReportItems.addAll(localReportItems);
    return ValidationReport.of(idCheck.getLeft(), allReportItems);
  }

  private Pair<String, ValidationReportItem> checkForUniqueProvidedCHOId(Model model) {
    final Set<String> ids = EdmExternalRecordIdExtractor.extractRecordIds(model);
    if (ids.isEmpty()) {
      return new ImmutablePair<>(null, new ValidationReportItem(null, null, null,
          "No unique provided CHO ID found.", ValidationIssueSeverity.ERROR));
    }
    if (ids.size() > 1) {
      return new ImmutablePair<>(null, new ValidationReportItem(null, null, null,
          "Multiple unique provided CHO ID found.", ValidationIssueSeverity.ERROR));
    }
    return new ImmutablePair<>(normalizeUri(ids.iterator().next()), null);
  }

  private List<ValidationReportItem> checkForUnsupportedTypes(Model model) {

    // Check whether the type is declared and supported. We query for a mapping from all
    // resources to their type (or absence thereof). We then cross-check with the supported types.
    // Note that we limit this to resources that the model makes any statements about, not resources
    // that are just external references (and may be dereferenced at a later point). We also
    // consider blank nodes (anonymous resources).
    final String typeMapQuery = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        SELECT DISTINCT ?resource ?type
        WHERE {
            ?resource ?pred ?object .
            OPTIONAL { ?resource rdf:type ?type } .
        }""";
    try (QueryExecution typeMapQueryExecution = QueryExecutionFactory
        .create(QueryFactory.create(typeMapQuery), model)) {
      final ResultSet results = typeMapQueryExecution.execSelect();
      final List<ValidationReportItem> validationItems = new ArrayList<>();
      results.forEachRemaining(result -> {
        final Node resourceNode = result.get("resource").asNode();
        final String type = Optional.ofNullable(result.get("type")).filter(RDFNode::isResource)
            .map(RDFNode::asResource).map(Resource::getURI).orElse(null);
        final String resourceString = resourceNode.isBlank() ? "Blank resource" : "Resource";
        if (type == null) {
          validationItems.add(new ValidationReportItem(toString(resourceNode), null, null,
              resourceString + " has no declared rdf:type.",
              ValidationIssueSeverity.ERROR));
        } else if (!SupportedResourceTypes.get().getSupportedResourceTypes().contains(type)) {
          validationItems.add(new ValidationReportItem(toString(resourceNode), null, null,
              resourceString + " has unsupported rdf:type " + type + ".",
              ValidationIssueSeverity.ERROR));
        }
      });
      return validationItems;
    }
  }

  private List<ValidationReportItem> checkForOrphanedResources(Model model) {

    // Check that there are no orphans: check that all resources are reachable from an aggregation
    // by any path of properties. The (<>|!<>) construction is a common shorthand for 'any property'.
    // Note that we limit this to resources that the model makes any statements about, not resources
    // that are just external references (and may be dereferenced at a later point). We also
    // consider blank nodes (anonymous resources) even though in the known representations it is
    // not possible to have a blank node that is not referenced.
    final String orphanDetectQuery = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX ore: <http://www.openarchives.org/ore/terms/>
        SELECT DISTINCT ?orphan
        WHERE {
            ?orphan ?pred ?object .
            FILTER NOT EXISTS {
                ?source (<>|!<>)* ?orphan .
                ?source rdf:type ore:Aggregation .
            } .
        }""";
    try (QueryExecution orphanDetectQueryExecution = QueryExecutionFactory
        .create(QueryFactory.create(orphanDetectQuery), model)) {
      final ResultSet results = orphanDetectQueryExecution.execSelect();
      final List<ValidationReportItem> validationItems = new ArrayList<>();
      results.forEachRemaining(result -> {
        final Node orphanNode = result.get("orphan").asNode();
        final String resourceString = orphanNode.isBlank() ? "Blank resource" : "Resource";
        validationItems.add(new ValidationReportItem(toString(orphanNode), null, null,
            resourceString + " is orphaned: it is not linked from elsewhere in the data.",
            ValidationIssueSeverity.WARNING));
      });
      return validationItems;
    }
  }
}
