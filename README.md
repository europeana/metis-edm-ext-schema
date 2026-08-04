# Metis EDM-external Schema 

This repository contains the functionality for validating EDM-external records. 
This code supports validation of RDF data in any standard representation
(Turtle, XML, etc.). It also contains some supporting functionality for analyzing 
EDM-external content (such as segmenting and extracting record IDs).

## Usage information

This repository offers functionality in three main areas through Java integration:

* Validation of individual EDM-external records through the functionality in 
  [EdmExternalValidator.java](src/main/java/eu/europeana/metis/edm/ext/schema/EdmExternalValidator.java).
* Segmenting of data dumps into individual records through the functionality in 
  [EdmExternalSegmenter.java](src/main/java/eu/europeana/metis/edm/ext/schema/EdmExternalSegmenter.java).
* Extraction of record ID(s) through the functionality in 
  [EdmExternalRecordIdExtractor.java](src/main/java/eu/europeana/metis/edm/ext/schema/EdmExternalRecordIdExtractor.java). 
  This is a specialized need that surfaces in the Metis pipeline.

Additionally, the SHACL shapes are usable on their own in any SHACL validator. They fully
conform to the SHACL standard. However, please note that they rely on the hierarchy in the
class definitions (in
[edm_ext_class_definitions.ttl](src/main/resources/schema/edm_ext_class_definitions.ttl))
being added to the source data, so that the (limited) SHACL type inference can take pace.

## The schema definitions

This repository contains SHACL shape definitions (represented in the Turtle format) that
implements the EDM-external schema and against which any records can be validated for EDM 
compliance. They are written in accordance with the following design principles:

* **User-friendliness**. The purpose of this validation is to provide feedback on a user's
  data's validity and correctness. The users' knowledge and experience with EDM, and
  RDF in general, can vary wildly. As such it is important to give clear feedback
  with appropriate and topical error and warning messages. To achieve this, the
  shapes need to be smaller and more numerous, each testing for just one
  constraint, on one property or resource, to give clear feedback. In practice
  this means that for instance the use of `sh:alternativePath` is limited, as
  this tends to obscure the contextual information for the user.

* **Maintainability**. The EDM standard is in active continuous development and
  developers need to be able to be able to adapt the schema easily and robustly to
  any changes. To avoid complex abstractions (e.g., requiring OWL reasoning),
  there is some repetition, mainly in the form of `sh:message` values. This could
  not be avoided without violating the usability considerations (see below).

* **Usability**. This repository offers easy-to-use access through the various entry
  points mentioned above. Additionally, consideration has been given to usage of
  the shapes in isolation (for instance outside the Java context). This is quite 
  possible, even though the requirement of including the class definitions slightly 
  complicates this use case.

## Future work

### Elimination of provenance-related non-RDF-compliant XML fields

The current XML schema defines two fields to be used in EDM external: `edm:wasGeneratedBy` and
`edm:confidenceLevel`. They are defined as optional attributes to resources and literals. This
makes the definition noncompliant with RDF. 

The way this is currently handled is that these attributes are stripped from the record before we 
apply this validation. Then, during transformation, the XSLT handles them and puts the triples in 
different proxy objects depending on the value. 

This is a temporary state of affairs that should some day be resolved in an RDF-compliant extension
to EDM. Once that is designed, we need to adjust the schema to cover this extension and then we can 
remove the code that strips these fields from the XML input.

### Normalization of XML representation

Validating XML records on an RDF level is less strict than validating it against an XML schema
on a structural level. To see this, consider the following construction:
```
<dc:creator>
  <edm:Agent rdf:about="http://www.example.com/XXXXXX">
    <skos:prefLabel>TEST</skos:prefLabel>
  </edm:Agent>
</dc:creator>
```
For RDF this is semantically equivalent to the `edm:Agent` being a top-level entity instead of 
nested in a `dc:creator`. But for the XML schema this is not the case, and the XSLT transformation
to EDM internal does not support this nested construction.

In RDF-based validation this will pass because the inner object is not anonymous, but has an ID. 
We therefore added code that will specifically check whether the objects are all top-level (and 
issue an error if they are not).

The ideal solution is to rework the transformation to operate on generic RDF and not 
just on XML. This will be a major undertaking, further complicated by the presence of 
the provenance fields (mentioned above). 

A permissive solution that is easier to implement is to convert a non-XML representation
to XML, then normalize this XML representation just before transformation. There is code
for this normalization in the `metis-commons-rdf` library (in `RdfXmlHierarchyNormalization`) 
that supports the provenance fields.

### Improve schema and validation usability outside Java applications

The way the functionality is currently designed, it is easiest to use it from Java 
applications because that interface supports proper application of the schema files
(like loading the class hierarchy into the data). There is nothing preventing other
applications from applying the schema files the same way. There are also the 'stricter'
validation checks done outside the schema (such as checking for unsupported 
resource types) and other supporting functionality implemented in Java. 

Europeana being a Java-centric organization, we will likely never support all functionality for 
other environments. However, there is demand for supplying the schema files in a way that does not
require complex initialization.

The main action we could take is to include eliminating the requirement to add the hierarchy 
to the source data before validation. It could be investigated whether the hierarchy could 
be built into the SHACL shapes in some way.

## References

* The full EDM documentation can be found here:
  https://europeana.atlassian.net/wiki/spaces/EF/pages/2916974597/Europeana+Data+Model
  including the various child articles.
* Additional information on the use of EDM (the 'Mapping Guidelines' can be found here:
  https://europeana.atlassian.net/wiki/spaces/EF/pages/987791389/EDM+-+Mapping+guidelines
  including the various child articles.
* The official SHACL definition can be found here: https://www.w3.org/TR/shacl/.
* The book _Validating RDF Data_, by Jose Emilio labra Gayo, Eric Prud’hommeaux, Iovka Boneva
  and Dimitris Kontokostas has a useful introductory chapter on SHACL and is available 
  online: https://book.validatingrdf.com/bookHtml011.html. 

## Latest update and pending work

* Any additional features in the existing (jibx) schema?
* This project should replace the schema links on the EDM page of Europeana Pro 
(https://pro.europeana.eu/page/edm-documentation)
