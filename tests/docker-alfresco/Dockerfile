FROM pdubois/docker-alfresco:v5.0.d

COPY alfresco-config.properties /tmp
RUN cat /tmp/alfresco-config.properties >> /opt/alfresco/tomcat/shared/classes/alfresco-global.properties

COPY alfresco-auth-amp.amp /opt/alfresco/amps

EXPOSE 8443
EXPOSE 8080
