# Metis EDM-external Schema 

This repository contains the functionality for validating EDM-external records. 
This code supports validation of RDF data in any standard representation
(Turtle, XML, etc.).

## The schema definitions

This repository contains SHACL shape definitions (represented in Turtle) that
implements the EDM-external schema and against
which any records can be validated for EDM compliance. They are written in
accordance with the following design principles:

* **Usability**. The purpose of this validation is to provide feedback on a user's
  data's validity and correctness. The users' knowledge and experience with EDM, and
  RDF in general, can vary wildly. As such it is important to give clear feedback
  with appropriate and topical error and warning messages. To achieve this, the
  shapes need to be smaller and more numerous, each testing for just one
  constraint, on one property or resource, to give clear feedback. In practice
  this means that for instance the use of `sh:alternativePath` is limited, as
  this tends to obscure the contextual information for the user.

* **Maintainability**. The EDM standard is in active continuous development and
  developers need to be able to be able to adapt the schema easily and robustly to
  any changes. To this end some compromises were made in the form of required
  pre-processing before validation against the shapes can take place:
    * Some `rdfs:subClassOf` definitions are added to the model before it
      is validated. They form a convenient class hierarchy that is then used in
      the shape definitions to make them more readable and understandable. The
      SHACL engine then picks them up automatically.
    * Within the shape definitions, common and recurring constructs are abstracted
      and then extended or inherited from, reducing as much as possible repetition
      among those many shape definitions. This hierarchy is implemented in accordance
      to the OWL standard. This means that OWL inference reasoning is required on
      the shape definitions before validating any data against them.

  > [!CAUTION]
  > Due to these maintainability compromises, the shape declarations should not be
  > used outside the context of this code base as a self-contained definition of EDM-external.
  > These pre-processing actions are required and form an integral part of the
  > EDM-external validation process.

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

The ideal solution is to be more permissive and normalize the XML before applying the 
transformation (or, rework the transformation to operate on generic RDF and not just on XML).
This cannot easily be done due to the presence of the provenance fields (mentioned above).
Once these fields have been eliminated, we can do a normalization step instead.

We can do this as follows. During the transformation phase (and just before applying the XSLT),  
we convert any model to RDF/XML using Jena, ensuring that all nested objects end up in the root 
node. Note: there is some code for this in the LOD project where we perform segmentation.
We will have to take care that we handle relative URIs properly.

## Latest update

This is still a trial version with limited rules.

Still lacking support:

* Detecting orphaned entities (probably requiring SPARQL)
* TODOs in schema: To be investigated.
* According to OEmbed profile:
  > "The edm:WebResource may have the ebucore:hasMimeType property with one of two
  > values: `application/json+oembed` or `application/xml+oembed`."

  In general we should (probably) allow all technical metadata fields in `edm:WebResource`?
  The idea being that all fields that are served by our own APIs should be allowed as input?


Further additions needed (these may also require inclusion in the EDM documentation below):

* https://europeana.atlassian.net/browse/MET-6997
* The various record API V3 requirements (as warnings)
* Multiple provenances - Data added by intermediate provider/aggregator.
* Technical metadata fields (profile)?
* Others? Also see https://pro.europeana.eu/page/edm-profiles.
* Others? Also see (subpages
  of) https://europeana.atlassian.net/wiki/spaces/EF/pages/1141932262/Classes+from+EDM+Profiles
* Any additional features in the existing (jibx) schema?
* We could show info-level warnings for the absence of recommended properties.

This project should replace the schema links on the EDM page of Europeana Pro 
(https://pro.europeana.eu/page/edm-documentation)

Full EDM documentation: https://europeana.atlassian.net/wiki/spaces/EF/pages/987791389/EDM+-+Mapping+guidelines

## Usage information

To be added.