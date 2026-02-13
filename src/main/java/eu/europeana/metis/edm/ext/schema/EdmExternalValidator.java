package eu.europeana.metis.edm.ext.schema;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

public class EdmExternalValidator {

  // This URL is reserved and with the unique ID should never occur in the wild.
  private static final String LOCAL_URL_BASE = "http://example.com/3a051336-f671-4e94-90db-45d3432181fb/";

  // TODO these can be static and initialized once for the VM.
  private final Shapes shapes;
  private final Model modelHierarchy;
  private final Set<String> supportedResourceTypes;

  public EdmExternalValidator() {
    final Model enhancedShapeModel = ModelFactory.createInfModel(ReasonerRegistry.getOWLReasoner(),
        ModelFactory.createDefaultModel().read("schema/edm_ext_shacl_shapes.ttl", Lang.TTL.getLabel()));

    // Parse the shapes.
    this.shapes = Shapes.parse(enhancedShapeModel);

    // Parse the class definitions and type categorization.
    this.modelHierarchy = ModelFactory.createDefaultModel().read(
        "schema/edm_ext_class_definitions.ttl", Lang.TTL.getLabel());

    // From the type categorization, extract the types that are supported: all subtypes of EdmClass.
    final String supportedTypesQuery = """
        PREFIX edm_ext_schema: <http://www.europeana.eu/metis/edm/ext/>
        PREFIX rdfs:           <http://www.w3.org/2000/01/rdf-schema#>
        SELECT DISTINCT ?type
        WHERE {
          ?type rdfs:subClassOf* edm_ext_schema:EdmClass .
          FILTER (!strStarts(str(?type), str(edm_ext_schema:))) .
        }
        """;
    try (QueryExecution supportedTypesQueryExecution = QueryExecutionFactory
        .create(QueryFactory.create(supportedTypesQuery), this.modelHierarchy)) {
      final Set<String> results = new HashSet<>();
      supportedTypesQueryExecution.execSelect().forEachRemaining(result ->
          results.add(Objects.requireNonNull(result.get("type").asResource().getURI())));
      this.supportedResourceTypes = Collections.unmodifiableSet(results);
    }
  }

  private static String normalizeUri(String uri) {
    return (uri != null && uri.startsWith(LOCAL_URL_BASE)) ?
        uri.substring(LOCAL_URL_BASE.length()) : uri;
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

  public ValidationReport validateSingleRecordTtl(String rdfTtlInput) {
    return validateSingleRecord(rdfTtlInput, Lang.TTL);
  }

  public ValidationReport validateSingleRecordXml(String rdfXmlInput) {
    final List<ValidationReportItem> preValidationItems = new ArrayList<>();
    final String normalizedXmlInput = RdfXmlPreValidationUtils.normalizeAndPreValidateXmlRecord(
        rdfXmlInput, preValidationItems::add);
    final ValidationReport report = validateSingleRecord(normalizedXmlInput, Lang.RDFXML);
    return ValidationReport.merge(report, preValidationItems);
  }

  private ValidationReport validateSingleRecord(String record, Lang inputLanguage) {

    // Parse the model
    final Model model = ModelFactory.createDefaultModel();
    try {
      model.read(new StringReader(record), LOCAL_URL_BASE, inputLanguage.getLabel());
    } catch (RuntimeException e) {
      return new ValidationReport(null, ValidationIssueSeverity.ERROR,
          List.of(new ValidationReportItem(null, null, null,
              "Could not parse input: " + e.getMessage(), ValidationIssueSeverity.ERROR)));
    }

    // Global analysis: parse and analyze the model as a whole.
    final Pair<String, ValidationReportItem> idCheck = checkForUniqueProvidedCHOId(model);
    final List<ValidationReportItem> unsupportedTypeCheck = checkForUnsupportedTypes(model);
    final ValidationReportItem orphanedResourcesCheck = checkForOrphanedResources(model);

    // Local analysis: validate the provided shapes. First, add the resource hierarchy.
    model.add(this.modelHierarchy);
    final List<ValidationReportItem> localReportItems = new ArrayList<>();
    ShaclValidator.get().validate(shapes, model.getGraph()).getEntries().forEach(entry ->
        localReportItems.add(new ValidationReportItem(toString(entry.focusNode()),
            toString(entry.resultPath()), toString(entry.value()), entry.message(),
            ValidationIssueSeverity.forSeverity(entry.severity()))));

    // Compile and run report.
    final List<ValidationReportItem> allReportItems = new ArrayList<>();
    Optional.ofNullable(idCheck.getRight()).ifPresent(allReportItems::add);
    allReportItems.addAll(unsupportedTypeCheck);
    Optional.ofNullable(orphanedResourcesCheck).ifPresent(allReportItems::add);
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

    // Check whether the type is known and supported. We query for a mapping from all known
    // resources to their type. We then cross-check with the supported types.
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
        final String resourceString =
            resourceNode.isBlank() ? "[BLANK RESOURCE]" : toString(resourceNode);
        if (type == null) {
          validationItems.add(new ValidationReportItem(toString(resourceNode), null, null,
              "Resource " + resourceString + " has no declared rdf:type.",
              ValidationIssueSeverity.ERROR));
        } else if (!this.supportedResourceTypes.contains(type)) {
          validationItems.add(new ValidationReportItem(toString(resourceNode), null, null,
              "Resource " + resourceString + " has unsupported rdf:type " + type + ".",
              ValidationIssueSeverity.ERROR));
        }
      });
      return validationItems;
    }
  }

  private ValidationReportItem checkForOrphanedResources(Model model) {

    // Check that there are no orphans: check that all resources are reachable from an aggregation.
    // This can be done by creating a simplified graph of which resource has a reference to which
    // other resource. Then check whether there are resources that can't be reached from an aggregation.

    // TODO return report
    return null;
  }
}
