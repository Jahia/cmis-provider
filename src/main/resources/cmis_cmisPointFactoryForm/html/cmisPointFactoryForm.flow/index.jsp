<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="mailSettings" type="org.jahia.services.mail.MailSettings"--%>
<%--@elvariable id="flowRequestContext" type="org.springframework.webflow.execution.RequestContext"--%>
<%--@elvariable id="cmisFactory" type="org.jahia.modules.external.cmis.admin.CMISMountPointFactory"--%>
<template:addResources type="javascript" resources="admin/angular.min.js"/>
<template:addResources type="javascript" resources="admin/app/folderPicker.js"/>
<template:addResources type="javascript" resources="cmis_mount/app.js"/>
<template:addResources type="css" resources="admin/app/folderPicker.css"/>


<script type="text/javascript">
    $(document).ready(function(){
        $('.admin_tooltip').tooltip()
    });
</script>
<div class="folderPickerApp" ng-app="cmisMount">
    <h2><fmt:message key="cmisFactory"/></h2>
    <%@ include file="errors.jspf" %>
    <fmt:message var="selectTarget" key="cmisFactory.selectTarget"/>
    <c:set var="i18NSelectTarget" value="${functions:escapeJavaScript(selectTarget)}"/>
    <div class="box-1" ng-controller="cmisMountEditCtrl"
         cmis-initiator="{'type': '${cmisFactory.type}', 'repositoryId' : '${cmisFactory.repositoryId}'}" cmis-type="cmisType" cmis-repositoryId="repositoryId"
         ng-init='init(${localFolders}, "${fn:escapeXml(cmisFactory.localPath)}", "localPath", true,"${i18NSelectTarget}")'>
        <form:form modelAttribute="cmisFactory" method="post">
            <fieldset title="type">
                <div class="container-fluid">
                    <div class="row-fluid">
                        <form:label path="type"><fmt:message key="cmisFactory.type"/> <span style="color: red">*</span></form:label>
                        <select id="type" name="type" ng-model="cmisType">
                            <option value=""><fmt:message key="cmisFactory.type.empty"/></option>
                            <option value="cmis"><fmt:message key="cmisFactory.type.cmis"/></option>
                            <option value="alfresco"><fmt:message key="cmisFactory.type.alfresco"/></option>
                        </select>&nbsp;<span class="admin_tooltip" data-placement="right" title="<fmt:message key="cmisFactory.type.tooltip"/>"><i class="icon-info-sign"></i></span>
                    </div>
                </div>
            </fieldset>
            <fieldset title="local" ng-if="cmisType">
                <div class="container-fluid">
                    <div class="row-fluid">
                        <form:label path="name"><fmt:message key="label.name"/> <span style="color: red">*</span></form:label>
                        <form:input path="name"/>
                    </div>
                    <div class="row-fluid" ng-show="cmisType == 'cmis'">
                        <form:label path="repositoryId"><fmt:message key="cmisFactory.repositoryId"/> <span style="color: red">*</span></form:label>
                        <input ng-model="repositoryId" id="repositoryId" name="repositoryId" type="text">
                    </div>
                    <div class="row-fluid">
                        <form:label path="user"><fmt:message key="cmisFactory.user"/></form:label>
                        <form:input path="user"/>
                    </div>
                    <div class="row-fluid">
                        <form:label path="password"><fmt:message key="cmisFactory.password"/></form:label>
                        <form:password path="password" showPassword="true"/>
                    </div>
                    <div class="row-fluid">
                        <form:label ng-show="cmisType == 'cmis'" path="url"><fmt:message key="cmisFactory.url"/> <span style="color: red">*</span></form:label>
                        <form:label ng-show="cmisType == 'alfresco'" path="url"><fmt:message key="cmisFactory.urlAlfresco"/> <span style="color: red">*</span></form:label>
                        <form:input path="url"/>
                    </div>
                    <div class="row-fluid" ng-show="cmisType == 'alfresco'">
                        <form:label path="publicUser"><fmt:message key="cmisFactory.publicUser"/></form:label>
                        <form:input path="publicUser"/>
                    </div>
                    <div class="row-fluid">
                        <form:label path="remotePath"><fmt:message key="cmisFactory.remotePath"/></form:label>
                        <form:input path="remotePath"/>
                    </div>
                    <div class="row-fluid">

                        <form:label path="slowConnection"><form:checkbox path="slowConnection"/>&nbsp;<fmt:message key="cmisFactory.slowConnection"/></form:label>
                    </div>
                    <div class="row-fluid">
                        <jsp:include page="/modules/external-provider/angular/folderPicker.jsp"/>
                    </div>
                </div>
            </fieldset>
            <fieldset>
                <div class="container-fluid">
                    <button class="btn btn-primary" type="submit" name="_eventId_save">
                        <span><fmt:message key="label.save"/></span>
                    </button>
                    <button class="btn" type="submit" name="_eventId_cancel">
                        <span><fmt:message key="label.cancel"/></span>
                    </button>
                </div>
            </fieldset>
        </form:form>
    </div>
</div>
