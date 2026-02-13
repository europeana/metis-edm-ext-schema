package eu.europeana.metis.edm.ext.schema;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

  /**
   * Creates a validation report for the given record ID and report items, where the severity will
   * be the highest severity encountered in any of the report items.
   *
   * @param recordId    The record ID. Can be null.
   * @param reportItems The report items. Can be null.
   * @return An instance.
   */
  public static ValidationReport of(String recordId, List<ValidationReportItem> reportItems) {
    final List<ValidationReportItem> nonNullItems = Optional.ofNullable(reportItems)
        .orElse(Collections.emptyList());
    return new ValidationReport(recordId, nonNullItems.stream().map(ValidationReportItem::severity)
        .max(ValidationIssueSeverity.comparator()).orElse(null), reportItems);
  }

  /**
   * Merges additional report items into an existing report to create a new report.
   *
   * @param report                The existing report.
   * @param additionalReportItems The additional report items.
   * @return A new report (a new instance).
   */
  public static ValidationReport merge(ValidationReport report,
      List<ValidationReportItem> additionalReportItems) {
    if (additionalReportItems == null || additionalReportItems.isEmpty()) {
      return report;
    }
    final List<ValidationReportItem> allItems = report.reportItems;
    allItems.addAll(additionalReportItems);
    return of(report.recordId, Collections.unmodifiableList(allItems));
  }
}
