package eu.europeana.metis.edm.ext.schema;

import java.io.StringReader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;

public class EdmExternalValidator {

  final Shapes SHAPES;

  public EdmExternalValidator() {
    this.SHAPES = Shapes.parse(RDFDataMgr.loadGraph("schema/shacl_edm.ttl"));
  }

  public void validate(String rdfXmlInput) {
    final Model model = ModelFactory.createDefaultModel();
    model.read(new StringReader(rdfXmlInput), "", Lang.RDFXML.getLabel());

    final ValidationReport report = ShaclValidator.get().validate(SHAPES, model.getGraph());
    ShLib.printReport(report);
    System.out.println();
    RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
  }
}
