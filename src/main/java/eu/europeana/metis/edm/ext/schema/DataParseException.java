package eu.europeana.metis.edm.ext.schema;

/**
 * Signifies an issue with parsing input data.
 */
public class DataParseException extends Exception {

  /**
   * Constructor.
   *
   * @param message The message.
   */
  public DataParseException(String message) {
    super(message);
  }

  /**
   * Constructor.
   *
   * @param message The message.
   * @param cause   The cause.
   */
  public DataParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
