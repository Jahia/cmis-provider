Jahia-cmis module contains external provider to mount external repositories using CMIS protocol.

1. Supported features
	1. Supported 2 base cmis types - document and folder with custom types inherited from this.
	2. Full support CRUD, rename, move for both types. Possibly to modify content and properties values.
	3. External provider supports path and id identification.
	4. Not supported ordering ( cmis does not have such feature).
	5. Support Searchable with restrictions. (see TestQuery.txt for demo queries)
		a. Join not implemented because we return only one node path per row.
		b. lower, upper, length, score - not supported
		c. not mapped properties work as null
		d. not implemented any specific for multivalue properties.
		e. Date type supports only if used cast expression, no auto converting.

CMIS not support languages attribute, so if Jahia add language restrictions we can see not mapped property in CMIS queries.

2. Configuration
In default configuration module mounted at /external-cmis-mapped.
As implementation CMIS protocol used Apache Chemistry. 
Module looks for connection parameters in general Jahia config. All configuration properties started with "org.apache.chemistry." prefix.
For connection parameters details see
In default configuration module declare to JCR types: cmis:document and cmis:folder to mapping relevant CMIS types.
You can configure mapping cmis types on jcr types and properties using Spring.
To change mapping you need overwrite CmisConfiguration bean.
CmisConfiguration contains typeMapping property with list of CmisTypeMapping beans.
CmisTypeMapping bean support hierarchy using parent or children properties to avoid duplication in property mapping.
For property mapping used CmisTypeMapping property by name "properties". It contains list of CmisPropertyMapping beans.
Each property may supports 3 modes ( "mode" ) - rwc. R - read , W - write (update), C - property may be set on creation. By default property has only read mode.

