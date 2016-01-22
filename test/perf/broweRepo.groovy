/**
 * Created by kevan
 */
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRNodeIteratorWrapper
import org.jahia.services.content.JCRNodeWrapper
import org.jahia.services.content.JCRSessionWrapper
import org.jahia.services.content.JCRTemplate
import org.jahia.services.content.decorator.JCRFileContent
import org.jahia.services.content.decorator.JCRFileNode

import javax.jcr.RepositoryException

double executionSum = 0
long executionMax = 0
long executionMin = 999999999;
def browseFile
def browseFolder

browseFile = { node ->
    JCRFileNode file = node;
    //println "Browsing file: ${file.path}"
    Map<String, String> props = file.getPropertiesAsString();
    //props.each{ k, v -> println "browsing file: ${file.path}, prop: ${k}:${v}" }

    JCRFileContent content = file.getFileContent()
    if(content != null) {
        //println "Browsing file: ${file.path}, download content ..."
        content.downloadFile()
        //println "Browsing file: ${file.path}, content downloaded"
    }
}

browseFolder = { node ->
    //println "Browsing folder: ${node.path}"
    JCRNodeIteratorWrapper iterator = node.getNodes();
    while (iterator.hasNext()) {
        JCRNodeWrapper child = iterator.next();
        if(child.isNodeType("jnt:folder")) {
            browseFolder(child)
        } else if(child.isNodeType("jnt:file"))  {
            browseFile(child)
        }
    }
}

def benchmark = { closure ->
    start = System.currentTimeMillis()
    closure.call()
    now = System.currentTimeMillis()
    now - start
}

for ( i in 1..30 ) {
    def duration = benchmark {
        JCRTemplate.instance.doExecuteWithSystemSession(new JCRCallback() {
            @Override
            Object doInJCR(JCRSessionWrapper jcrSessionWrapper) throws RepositoryException {
                JCRNodeWrapper node = jcrSessionWrapper.getNode("/sites/mySite/files/alfresco");
                browseFolder(node);
            }
        })
    }
    executionMin = Math.min(duration, executionMin);
    executionMax = Math.max(duration, executionMax);
    println "execution took ${duration} ms"
    executionSum += Double.valueOf( duration );
}
double average = executionSum/30;
println "average ${average} ms"
println "max ${executionMax} ms"
println "min ${executionMin} ms"