package eu.europeana.metis.edm.ext.schema;

import java.io.StringReader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
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

  public EdmExternalValidator() {
    final Model enhancedShapeModel = ModelFactory.createInfModel(ReasonerRegistry.getOWLReasoner(),
        ModelFactory.createDefaultModel().read("schema/edm_ext_shacl_shapes.ttl"));
    this.shapes = Shapes.parse(enhancedShapeModel);
    this.modelHierarchy = ModelFactory.createDefaultModel().read(
        "schema/edm_ext_class_definitions.ttl");
  }

  public void validate(String rdfXmlInput) {
    final Model model = ModelFactory.createDefaultModel();
    model.read(new StringReader(rdfXmlInput), "", Lang.RDFXML.getLabel());
    model.add(this.modelHierarchy);

    // TODO return report.
    final ValidationReport report = ShaclValidator.get().validate(shapes, model.getGraph());
    ShLib.printReport(report);
    System.out.println();
    RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
  }
}
