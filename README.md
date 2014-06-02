# Jahia-CMIS module

## Overview

### CMIS

Content Management Interoperability Services (CMIS) is an open standard that allows different content management systems to inter-operate over the Internet.
Specifically, CMIS defines an abstraction layer for controlling diverse document management systems and repositories using web protocols.

CMIS defines a domain model plus bindings that can be used by applications.
OASIS, a web standards consortium, approved CMIS as an OASIS Specification on May 1, 2010.
CMIS 1.1 has been approved as an OASIS specification on December 12, 2012.

CMIS provides a common data model covering typed files and folders with generic properties that can be set or read.
There is a set of services for adding and retrieving documents ('objects').
There may be an access control system, a checkout and version control facility, and the ability to define generic relations.
Three protocol bindings are defined, one using WSDL and SOAP, another using AtomPub,[4] and a last browser-friendly one using JSON.
The model is based on common architectures of document management systems.

### Goals

The goals of this project are as follows:

 - Integrate CMIS supported repository.
 - Implement full CRUD support.
 - Implement Search as full as possible.

### Supported features

1. 2 base CMIS types (document and folder) and custom types inherited from them are supported.
2. Full support CRUD, rename, move for supported types. It is possible to modify content and properties values.
3. Both path and id identification are supported by external provider.
4. Ordering is not supported, for CMIS does not support such feature.
5. Searchable is supported  with restrictions (see TestQuery.txt for demo queries).
    - Join is not implemented. We return only one node path per row.
    - lower, upper, length, score - are not supported.
    - not mapped properties acts as null.
    - any specific behaviour for multivalue properties is not implemented.
    - date type is supported only if cast expression is used (no auto converting).
6. Configurable mapping for custom types.
CMIS not supports languages attribute, so if Jahia will add language restrictions there will be not mapped property in CMIS queries.

### Implementation tips

As implementation CMIS protocol used Apache Chemistry.
In default configuration module mounted at /external-cmis-mapped.
To map CMIS types on JCR we use cmis:folder and cmis:document. Common properties separate in cmis:base mixin.

If we need to map some custom CMIS type we will need to extend cmis:folder or cmis:document.
Mapping for this type must be configured in spring. CmisTypeMapping support inheritance, so no property mapping duplication will need.

---

## Configuration

Jahia CMIS datasource is full configurable with spring config using special CmisConfiguration bean.
CmisConfiguration bean has 2 properties:
 - repositoryProperties - map of connection related configuration properties.
 By default, repositoryProperties are configured for usage of general jahia.properties
 - typeMapping - list of CmisTypeMapping beans. There are 2 base mappings: for cmis:document and cmis:folder.

### Connection configuration
Module looks for connection parameters in general Jahia config. All configuration properties are started with "org.apache.chemistry." prefix.
(see https://chemistry.apache.org/java/examples/example-create-session.html for connection parameters details).

#### Connection configuration example

```
org.apache.chemistry.opencmis.binding.spi.type=atompub

org.apache.chemistry.opencmis.session.repository.id=A1
org.apache.chemistry.opencmis.user=dummyuser
org.apache.chemistry.opencmis.password=dummysecret
org.apache.chemistry.opencmis.binding.atompub.url=http://localhost:18080/inmemory/atom11
```

Comments line by line:
 1. Binding type  - AtomPub (Set by default) other possible values are: webservices, browser, custom
 2. Repository id
 3. Login
 4. Password
 5. Access URL
This is minimal list of parameters required to get access.
Depending on binding type, list of required parameters differ.

### Mapping configuration

CMIS - JCR mapping is configurable with spring configuration files.
For each JCR type there must be CmisTypeMapping bean. Each CmisTypeMapping may have list of property mappings.
Each property may support 3 modes ( "mode" ) - rwc. R - read , W - write (update), C - property will be set on creation. By default property has only read mode.

#### Property mapping example

```
<bean class="org.jahia.modules.external.cmis.CmisTypeMapping" id="cmis_document" p:jcrName="cmis:document" p:cmisName="cmis:document">
    <property name="properties">
        <list >
            <bean p:cmisName="cmis:createdBy" p:jcrName="jcr:createdBy" class="org.jahia.modules.external.cmis.CmisPropertyMapping" />
            <bean p:cmisName="cmis:description" p:jcrName="cmis:description" class="org.jahia.modules.external.cmis.CmisPropertyMapping" p:mode="rwc" />
            <bean p:cmisName="cmis:contentStreamFileName" p:jcrName="cmis:contentStreamFileName" class="org.jahia.modules.external.cmis.CmisPropertyMapping" p:mode="rw"/>
        </list>
    </property>
</bean>
```

**This fragment is not real mapping example. We use it for documentation purposes. **

In this fragment we map CMIS *cmis:createdBy* property on JCR *jcr:createdBy* property as read only.
In the next line *cmis:description* is mapped  as JCR property with the same name in read, create and write modes.
In the next line *cmis:contentStreamFileName* is mapped as JCR property with the same name in read and write modes. For this property create mode is not set.
it means you can't set value for this property on node creation, but it can be updated later.
If the create mode is set without write mode, it is possible to set property value on creation, but it can not be updated later.


CmisTypeMapping beans may be organized in trees to support inheritance.
Child been will inherit all parent attribute mappings. Local attribute mapping will override inherited.
Inheritance may be configured in two ways:
- by using children property with list of embedded beans;
- by using separate beans linked by parent property.

#### Example 1
```
<property name="typeMapping">
    <list>
    <bean class="org.jahia.modules.external.cmis.CmisTypeMapping" id="cmis_document" p:jcrName="cmis:document"  p:cmisName="cmis:document">
        <property name="children">
            <list>
                <bean class="org.jahia.modules.external.cmis.CmisTypeMapping" p:jcrName="cmis:image" p:cmisName="cmis:image">
                </bean>
            </list>
        </property>
    </bean>
    </list>
</property>
```

#### Example 2
```
<property name="typeMapping">
    <list>
    <bean class="org.jahia.modules.external.cmis.CmisTypeMapping" id="cmis_document" p:jcrName="cmis:document"  p:cmisName="cmis:document">
    </bean>
    <bean class="org.jahia.modules.external.cmis.CmisTypeMapping" p:jcrName="cmis:image" p:cmisName="cmis:image">
        <property name="parent" ref="cmis_document"/>
    </bean>
    </list>
</property>
```
First way is more visual.
Second way is more flexible. You can inherit mappings even from other modules. **Don't forget to include parent bean in typeMapping list, too.**

---

## Testing environment tips.

The easiest way to create test environment is to use *OpenCMIS InMemory Repository* https://chemistry.apache.org/java/developing/repositories/dev-repositories-inmemory.html
InMemory Repository can be deployed either in separate Tomcat or in the same as for Jahia. Separate Tomcat is preferable because webapp's startup order can not be configured.
In other case you must be sure you don't access mount point on startup.

*OpenCMIS Workbench* may be used as CMIS client.

All CRUD operations are accessible from Repository Manager.
For search testing JCR query tool can be used. JCR-SQL2 queries covering all implemented search functionality can be found in docs/TestQuery.txt.

### Testing environment installation.

1. Install Tomcat for CMIS
2. Unpack chemistry-opencmis-server-inmemory-xxx.war into tomcat/webapp/inmemory folder. Repository config file is inmemory\WEB-INF\classes\repository.properties.
 Default configuration is ok.
3. Deploy Jahia with Jahia CMIS module.
4. Add next 4 lines to jahia.properties. Configure port, depending on your tomcat config.
```
org.apache.chemistry.opencmis.session.repository.id=A1
org.apache.chemistry.opencmis.user=dummyuser
org.apache.chemistry.opencmis.password=dummysecret
org.apache.chemistry.opencmis.binding.atompub.url=http://localhost:18080/inmemory/atom11
```
5. Start Jahia.

There will be CMIS repository mounted in /external-cmis-mapped.

---

## TODO:
 - Renditions;
 - Mount point configuration from JCR.

