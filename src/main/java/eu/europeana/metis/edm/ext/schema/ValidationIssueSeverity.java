package eu.europeana.metis.edm.ext.schema;

import java.util.Comparator;
import org.apache.jena.shacl.validation.Severity;

/**
 * The various levels of severity supported for validation issues.
 */
public enum ValidationIssueSeverity {

  /**
   * Error items: the record can not progress through the pipeline.
   */
  ERROR(2),
  /**
   * Warning items: the record may progress further through the pipeline. The presence of the issue
   * may have consequences further downstream.
   */
  WARNING(1),

  /**
   * Information items: the record may progress further through the pipeline. The presence of the
   * issue will not have consequences further downstream.
   */
  INFO(0);

  private final int order;

  ValidationIssueSeverity(int order) {
    this.order = order;
  }

  private int getOrder() {
    return order;
  }

  /**
   * Converts a SHACL severity level to an instance of this enum.
   *
   * @param severity The SHACL severity level.
   * @return The instance of this enum.
   * @throws IllegalArgumentException if an unknown or <code>null</code> severity was passed.
   */
  public static ValidationIssueSeverity forSeverity(Severity severity) {
    if (Severity.Info.equals(severity)) {
      return INFO;
    }
    if (Severity.Warning.equals(severity)) {
      return WARNING;
    }
    if (Severity.Violation.equals(severity)) {
      return ERROR;
    }
    throw new IllegalArgumentException("Unknown severity: " + severity);
  }

  /**
   * @return A comparator that can compare severity levels by severity: the most severe level is
   * 'high', the least severe level is 'low'.
   */
  public static Comparator<ValidationIssueSeverity> comparator() {
    return Comparator.comparing(ValidationIssueSeverity::getOrder);
  }
}
