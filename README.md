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
    * Within the shape definitions common and recurring constructs are abstracted
      and then extended or inherited from, reducing as much as possible repetition
      among those many shape definitions. This hierarchy is implemented in accordance
      to the OWL standard. This means that OWL inference reasoning is required on
      the shape definitions before validating any data against them.

  > [!CAUTION]
  > The shape declarations should therefore not be used outside the context
  > of this code base as a self-contained definition of EDM-external. These
  > pre-processing actions are required and form an integral part of the
  > EDM-external validation process.

## Latest update

This is still a trial version with limited rules.

Still lacking support:
* Some EDM features as documented
* Detecting orphaned entities (probably requiring SPARQL)
* TODOs in schema: To be investigated.
* We could look into separating the shapes into a file that can be used without importing the hierarchy, 
and can be used easily by externals, and then a bit for which the hierarchy is necessary. 

Further additions needed (these may also require inclusion in the EDM documentation below):

* The various record API V3 requirements (as warnings)
* 3D profile (see https://europeana.atlassian.net/browse/MET-6731)
* IIIF profile
* OEmbed profile
* PID profile
* Technical metadata fields (profile)
* Others? Also see https://pro.europeana.eu/page/edm-profiles.
* Others? Also see (subpages
  of) https://europeana.atlassian.net/wiki/spaces/EF/pages/1141932262/Classes+from+EDM+Profiles
* Any additional features in the existing schema?
* We could show info-level warnings for the absence of recommended properties.
* Note: the new schema opens up the possibility to accept construction like this:
  ```
  <dc:creator>
    <edm:Agent rdf:about="http://www.example.com/XXXXXX">
      <skos:prefLabel>TEST</skos:prefLabel>
    </edm:Agent>
  </dc:creator>
  ```
  This will work because the inner object is not anonymous, but has an ID. But how will
  transformation support this? We need class `EdmExternalNormalizer` which performs normalisation
  before transformation, consisting of the following:
    * Converting any model to RDF/XML
    * Ensuring that all nested objects end up in the root node
    * Converting relative URIs to absolute ones (using the `base` parameter in the
      `Model.read(...)` methods).

This project should replace the schema links on the EDM page of Europeana Pro 
(https://pro.europeana.eu/page/edm-documentation)

Full EDM documentation: https://europeana.atlassian.net/wiki/spaces/EF/pages/987791389/EDM+-+Mapping+guidelines

## Usage information

To be added.