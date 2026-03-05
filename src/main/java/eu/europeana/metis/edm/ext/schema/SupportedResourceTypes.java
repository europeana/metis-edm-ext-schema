package eu.europeana.metis.edm.ext.schema;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;

/**
 * This class represents the hierarchy of supported types. The supported types are types that are
 * expected to occur in our data. In addition, this model contains broader types that are used to
 * connect them and create a hierarchy.
 */
public class SupportedResourceTypes {

  private static SupportedResourceTypes instance = null;

  private final Model typeHierarchyModel;
  private final Set<String> supportedResourceTypes;

  private SupportedResourceTypes() {

    // Parse the class definitions and type categorization.
    this.typeHierarchyModel = ModelFactory.createDefaultModel().read(
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
        .create(QueryFactory.create(supportedTypesQuery), this.typeHierarchyModel)) {
      final Set<String> results = new HashSet<>();
      supportedTypesQueryExecution.execSelect().forEachRemaining(result ->
          results.add(Objects.requireNonNull(result.get("type").asResource().getURI())));
      this.supportedResourceTypes = Collections.unmodifiableSet(results);
    }
  }

  /**
   * Gets the one instance of this class.
   *
   * @return Instance of this class.
   */
  public static synchronized SupportedResourceTypes get() {
    if (instance == null) {
      instance = new SupportedResourceTypes();
    }
    return instance;
  }

  /**
   * This method returns a string of supported types. This will just include the types that are
   * expected to occur in actual data, not the internal types that are used to build the hierarchy.
   *
   * @return The supported types in an unmodifiable set.
   */
  public Set<String> getSupportedResourceTypes() {
    return supportedResourceTypes;
  }

  /**
   * This method adds the full type hierarchy to a provided model.
   *
   * @param model The model to which to add the type hierarchy.
   */
  public void addTypeHierarchyToModel(Model model) {
    model.add(typeHierarchyModel);
  }
}
