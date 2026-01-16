package eu.europeana.metis.edm.ext.schema;

/**
 * <p>Represents one detected issue. If an issue is detected multiple times (e.g. for multiple
 * fields), multiple report items shouuld be present in the report, one for each detected instance.
 * </p>
 * <p>
 * A validation issue typically pertains to a specific statement in the data, which in RDF is called
 * a (semantic) triple. They are of the form:
 * </p>
 * <p>
 * <code>subject - predicate - object</code>
 * </p>
 * <p>
 * This report item contains all these values for the triple that is the cause of this validation
 * item. If the validation item is related to a resource and not a triple, the <code>subject</code>
 * value will be the resource URI, if available. The other two values will be <code>null</code>. If
 * the validation item is related to a global issue (pertaining to the data as a whole), all three
 * values of the triple will be <code>null</code>.
 * </p>
 *
 * @param subject   The subject of the triple (a resource IRI) that caused the issue, if applicable.
 *                  Otherwise <code>null</code> (e.g., in case the subject is a blank node or the
 *                  item relates to a global issue).
 * @param predicate The predicate of the triple (a resource IRI) that caused the issue, if
 *                  applicable. Otherwise <code>null</code> (e.g., in case the issue does not
 *                  pertain to a specific triple).
 * @param object    The object of the triple (a resource IRI or literal value) that caused the
 *                  issue, if applicable. Otherwise <code>null</code> (e.g., in case the issue does
 *                  not pertain to a specific triple, or the object is a blank node).
 * @param message   The message explaining the issue.
 * @param severity  The severity of the issue.
 */
public record ValidationReportItem(String subject, String predicate, String object,
                                   String message, ValidationIssueSeverity severity) {

}
