package eu.europeana.metis.edm.ext.schema;

import java.util.Set;
import org.apache.jena.riot.Lang;

/**
 * Enum containing the supported representations.
 */
public enum Representation {

  XML(Lang.RDFXML, Set.of("application/xml", "text/xml"), ".xml"),
  TTL(Lang.TTL, Set.of("text/ttl"), ".ttl");

  private final Lang lang;
  private final Set<String> mimeTypes;
  private final String fileExtension;

  Representation(Lang lang, Set<String> mimeTypes, String fileExtension) {
    this.lang = lang;
    this.mimeTypes = mimeTypes;
    this.fileExtension = fileExtension;
  }

  public Lang getLang() {
    return lang;
  }

  public Set<String> getMimeType() {
    return mimeTypes;
  }

  public String getFileExtension() {
    return fileExtension;
  }
}
