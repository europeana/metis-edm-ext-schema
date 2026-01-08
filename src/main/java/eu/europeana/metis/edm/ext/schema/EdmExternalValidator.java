package eu.europeana.metis.edm.ext.schema;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;

public class EdmExternalValidator {

  final Shapes shapes;
  final Model modelHierarchy;
  final Set<String> supportedResourceTypes;

  public EdmExternalValidator() {
    final Model enhancedShapeModel = ModelFactory.createInfModel(ReasonerRegistry.getOWLReasoner(),
        ModelFactory.createDefaultModel().read("schema/edm_ext_shacl_shapes.ttl"));

    // Parse the shapes.
    this.shapes = Shapes.parse(enhancedShapeModel);

    // Parse the class definitions and type categorization.
    this.modelHierarchy = ModelFactory.createDefaultModel().read(
        "schema/edm_ext_class_definitions.ttl");

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
      supportedTypesQueryExecution.execSelect()
          .forEachRemaining(result -> results.add(result.get("type").toString()));
      this.supportedResourceTypes = Collections.unmodifiableSet(results);
    }
  }

  public void validate(String rdfXmlInput) {

    // Parse the model
    final Model model = ModelFactory.createDefaultModel();
    try {
      model.read(new StringReader(rdfXmlInput), "", Lang.RDFXML.getLabel());
    } catch (RuntimeException e) {
      System.out.println("Could not parse input: " + e.getMessage());
      e.printStackTrace();
      return;
    }

    // Global analysis: parse and analyze the model as a whole.
    checkForUnsupportedTypes(model);
    checkForOrphanedResources(model);

    // Local analysis: validate the provided shapes. First, add the resource hierarchy.
    model.add(this.modelHierarchy);
    final ValidationReport report = ShaclValidator.get().validate(shapes, model.getGraph());
    ShLib.printReport(report);
    System.out.println();
    RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);

    // TODO compile and return report.
  }

  private void checkForUnsupportedTypes(Model model) {

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
      results.forEachRemaining(result -> {
        final boolean isBlankResource = !result.get("resource").isURIResource();
        final String resource =
            isBlankResource ? "[BLANK RESOURCE]" : result.get("resource").toString();
        final String type = Optional.ofNullable(result.get("type")).map(RDFNode::toString)
            .orElse(null);
        if (type == null) {
          System.out.println("Resource " + resource + " has no type.");
          System.out.println();
        } else if (!this.supportedResourceTypes.contains(type)) {
          System.out.println("Resource " + resource + " has unsupported type " + type + ".");
          System.out.println();
        }
      });
    }
  }

  private void checkForOrphanedResources(Model model) {

    // Check that there are no orphans: check that all resources are reachable from an aggregation.
    // This can be done by creating a simplified graph of which resource has a reference to which
    // other resource. Then check whether there are resources that can't be reached from an aggregation.

    // TODO return report
  }
}
