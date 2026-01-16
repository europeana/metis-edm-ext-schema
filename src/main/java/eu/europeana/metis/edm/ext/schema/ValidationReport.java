package eu.europeana.metis.edm.ext.schema;

import java.util.List;

/**
 * The report that results from a record validation.
 *
 * @param recordId    The ID of the record that was encountered. Can be <code>null</code> if no
 *                    record ID was found, or multiple were found. In this case a report item to
 *                    that effect will be present in the <code>reportItems</code> list.
 * @param severity    The severity of the issues encountered. In practice this is the highest
 *                    severity encountered in any of the items in the <code>reportItems</code> list.
 *                    If no issues are reported, this value is <code>null</code>.
 * @param reportItems The list of report items: issues that were detected. Can be emtpy, but is
 *                    never <code>null</code>.
 */
public record ValidationReport(String recordId, ValidationIssueSeverity severity,
                               List<ValidationReportItem> reportItems) {

}
