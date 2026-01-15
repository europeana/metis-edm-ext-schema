package eu.europeana.metis.edm.ext.schema;

import java.util.HashSet;
import java.util.Set;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;

public class EdmExternalRecordIdExtractor {

  private EdmExternalRecordIdExtractor() {
  }

  public static Set<String> extractRecordIds(String data) {
    // TODO use apache RIOT. Some classes:
    //  https://javadoc.io/doc/org.apache.jena/jena-arq/3.5.0/org/apache/jena/riot/package-summary.html
    //  https://javadoc.io/doc/org.apache.jena/jena-arq/3.5.0/org/apache/jena/riot/ReaderRIOT.html
    //  https://javadoc.io/doc/org.apache.jena/jena-arq/3.5.0/org/apache/jena/riot/ReaderRIOTFactory.html
    //  https://javadoc.io/doc/org.apache.jena/jena-arq/3.5.0/org/apache/jena/riot/system/StreamRDF.html
    throw new UnsupportedOperationException();
  }

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
