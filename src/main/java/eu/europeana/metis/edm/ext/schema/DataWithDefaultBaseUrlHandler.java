package eu.europeana.metis.edm.ext.schema;

/**
 * Classes that extend this class have easy access to functionality around the default base URl.
 * When loading data, {@link #DEFAULT_BASE_URL} can be used as base URL for local references in the
 * data. The method {@link #normalizeUri(String)} can then be used to return these references to
 * their original (local) form before presenting them to the user.
 */
public abstract class DataWithDefaultBaseUrlHandler {

  // This URL is reserved and with the unique ID should never occur in the wild.
  static final String DEFAULT_BASE_URL = "http://example.com/3a051336-f671-4e94-90db-45d3432181fb/";

  static String normalizeUri(String uri) {
    return (uri != null && uri.startsWith(DEFAULT_BASE_URL)) ?
        uri.substring(DEFAULT_BASE_URL.length()) : uri;
  }
}
