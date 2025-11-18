# metis-edm-ext-schema

Trial version with limited rules.

Still lacking support:
* Some EDM features as documented
* Detecting orphaned entities (probably requiring SPARQL)
* TODOs in schema: To be investigated.
* We could look into separating the shapes into a file that can be used without importing the hierarchy, and can be used easily by externals, and then a bit for which the hierarchy is necessary. 

Further additions needed (these may also require inclusion in the EDM documentation below):
* The various record API V3 requirements (as warnings) 
* 3D profile (see https://europeana.atlassian.net/browse/MET-6731)
* IIIF profile
* OEmbed profile
* PID profile
* Technical metadata fields (profile)
* Others? Also see https://pro.europeana.eu/page/edm-profiles.
* Others? Also see (subpages of) https://europeana.atlassian.net/wiki/spaces/EF/pages/1141932262/Classes+from+EDM+Profiles
* Any additional features in the existing schema?

This project should replace the schema links on the EDM page of Europeana Pro 
(https://pro.europeana.eu/page/edm-documentation)

Full EDM documentation: https://europeana.atlassian.net/wiki/spaces/EF/pages/987791389/EDM+-+Mapping+guidelines

