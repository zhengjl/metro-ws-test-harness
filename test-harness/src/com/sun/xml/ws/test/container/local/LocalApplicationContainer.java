package com.sun.xml.ws.test.container.local;

import com.sun.istack.NotNull;
import com.sun.xml.ws.test.World;
import com.sun.xml.ws.test.container.Application;
import com.sun.xml.ws.test.container.ApplicationContainer;
import com.sun.xml.ws.test.container.DeployedService;
import com.sun.xml.ws.test.container.DeploymentContext;
import com.sun.xml.ws.test.container.local.jelly.SunJaxwsInfoBean;
import com.sun.xml.ws.test.container.local.jelly.WebXmlInfoBean;
import com.sun.xml.ws.test.model.TestEndpoint;
import com.sun.xml.ws.test.model.TestService;
import com.sun.xml.ws.test.util.AptWrapper;
import com.sun.xml.ws.test.util.JavacWrapper;
import com.sun.xml.ws.test.wsimport.WsTool;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.XMLOutput;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ApplicationContainer} for the local transport.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalApplicationContainer implements ApplicationContainer {

    private final WsTool wsimport;
    private final JellyContext jellyContext = new JellyContext();

    public LocalApplicationContainer(WsTool wscompile) {
        this.wsimport = wscompile;
    }

    public void start() {
        // noop
    }

    public void shutdown() {
        // noop
    }

    @NotNull
    public Application deploy(DeployedService service) throws Exception {
        //if(!wsimport.isNoop()) {
        //    // clean up the artifacts before
        //    FileUtil.deleteRecursive(service.workDir);
        //}

        compileServer(service);
        prepareWarFile(service);
        return new LocalApplication(service);
    }

    /**
     * Generates artifacts from WSDL (if any), then compile them
     * to javac.
     *
     * The compiled class files will be stored inside {@link DeploymentContext#workDir}.
     */
    private void compileServer(DeployedService service) throws Exception {
        String sourceDir = service.service.baseDir.getAbsolutePath();
        String destDir = service.buildClassesDir.getAbsolutePath();

        // Service starting from WSDL
        if(service.service.wsdl!=null) {
            //   Use 'wsimport' and 'javac'
            ArrayList<String> options = new ArrayList<String>();
            //Add customization files
            for (File custFile: service.service.customizations) {
                options.add("-b");
                options.add(custFile.getAbsolutePath());
            }

            //Other options
            options.add("-s");
            options.add(service.workDir.getAbsolutePath());
            options.add("-Xnocompile");
            options.add(service.service.wsdl.getAbsolutePath());
            System.out.println("wsdl = " + service.service.wsdl.getAbsolutePath());
            wsimport.invoke(options.toArray(new String[0]));
        }
        // both cases
        if(!wsimport.isNoop()) {
            JavacWrapper javacWrapper = new JavacWrapper();
            javacWrapper.init(sourceDir + ":" + service.workDir.getAbsolutePath(), destDir);
            javacWrapper.execute();
        }
        // Service starting from Java
        if(service.service.wsdl==null) {
            // Use wsgen to generate the artifacts
            if(!wsimport.isNoop()) {
                // TODO: UNHACKIFY
                WsTool wsgen = WsTool.createWsGen(null);
                System.out.println("service workdir path = " + service.workDir.getAbsolutePath());
//                SunJaxwsInfoBean infoBean = new SunJaxwsInfoBean(service);
                //for (TestEndpoint endpt : service.service.endpoints) {
                    ArrayList<String> options = new ArrayList<String>();
                    options.add("-wsdl");
                    options.add("-verbose");
                    options.add("-r");
                    options.add(service.buildClassesDir.getAbsolutePath());
                    options.add("-cp");
                    String path = service.buildClassesDir.getAbsolutePath() + File.pathSeparatorChar + World.toolClasspath + File.pathSeparatorChar + World.runtimeClasspath;
                    System.out.println("wsgen classpath arg = " + path);
                    options.add(path);
                    options.add("-s");
                    options.add(service.buildClassesDir.getAbsolutePath());
                    options.add("-d");
                    options.add(service.buildClassesDir.getAbsolutePath());
                    // options.add(endpt.className);
                    options.add("fromjava.server.AddNumbersImpl");
                    wsgen.invoke(options.toArray(new String[0]));
                //}
            }
        }
    }

    /**
     * Prepares an exploded war file image for this service.
     */
    private void prepareWarFile(DeployedService service) throws Exception {

        generateSunJaxWsXml(service);
        generateWebXml(service);

        Project p = new Project();
        p.init();

        /*
            Jar warFile = new Jar();
            warFile.setProject(p);
            warFile.setDestFile(new File(context.workDir, service.parent.shortName + ".war"));
        */
        // warFile.setWebxml(new File(context.workDir, "web.xml"));

        Copy copy = new Copy();
        copy.setProject(p);
        FileSet classesSet = new FileSet();
        classesSet.setDir(service.buildClassesDir);
        copy.addFileset(classesSet);
        copy.setTodir(new File(service.webInfDir,"classes"));
        copy.execute();

        if (service.service.wsdl != null) {
            patchWsdl(service);
        }

        /* Not really necessary for local transport case
         *
            ZipFileSet  tmpZipFileSet = new ZipFileSet();
            tmpZipFileSet.setDir(new File(context.workDir,"WEB-INF"));
            //warFile.addWebinf(tmpZipFileSet);
            warFile.addZipfileset(tmpZipFileSet);
            warFile.execute();
        */

        // TODO: resources directory?

        // TODO: anything special needed when creating the manifest?
    }

    /**
     * This method uses Jelly to write the sun-jaxws.xml file. The
     * template file is sun-jaxws.jelly. The real work happens
     * in the SunJaxwsInfoBean object which supplies information
     * to the Jelly processor through accessor methods.
     *
     * @see com.sun.xml.ws.test.container.local.jelly.SunJaxwsInfoBean
     */
    private void generateSunJaxWsXml(DeployedService service) throws Exception {

        OutputStream outputStream =
            new FileOutputStream(new File(service.webInfDir, "sun-jaxws.xml"));
        XMLOutput output = XMLOutput.createXMLOutput(outputStream);
        SunJaxwsInfoBean infoBean = new SunJaxwsInfoBean(service);
        jellyContext.setVariable("data", infoBean);
        jellyContext.runScript(getClass().getResource("jelly/sun-jaxws.jelly"),
            output);
        output.flush();
    }

    /**
     * This method uses Jelly to write the web.xml file. The
     * template file is web.jelly. The real work happens
     * in the WebXmlInfoBean object which supplies information
     * to the Jelly processor through accessor methods.
     *
     * @see com.sun.xml.ws.test.container.local.jelly.WebXmlInfoBean
     */
    private void generateWebXml(DeployedService service) throws Exception {

        OutputStream outputStream =
            new FileOutputStream(new File(service.webInfDir, "web.xml"));
        XMLOutput output = XMLOutput.createXMLOutput(outputStream);
        WebXmlInfoBean infoBean = new WebXmlInfoBean(service.parent);
        jellyContext.setVariable("data", infoBean);
        jellyContext.runScript(getClass().getResource("jelly/web.jelly"),
            output);
        output.flush();
    }

    /**
     * This is ugly but needed to be working quickly. A faster
     * way to do this would be to read in the old wsdl in stax
     * and write out the new one as we go. (TODO)
     *
     * Fix the address in the wsdl. This doesn't take into
     * account multiple endpoints, url patterns, etc. A
     * big TODO. Also, this could be a lot faster. To set
     * the ports and url patterns correctly, we would need
     * to parse the sun-jaxws.xml file, or keep the bean
     * around used by the Jelly code to query it. Maybe good
     * into to put in our memory model.
     */
    private void patchWsdl(DeployedService service) throws Exception {
        File wsdlDir = new File(service.webInfDir,"wsdl");
        wsdlDir.mkdir();
        File wsdl = service.service.wsdl;

        File dest = new File(wsdlDir, wsdl.getName());

        Document doc = new SAXReader().read(wsdl);
        List<Element> ports = doc.getRootElement().element("service").elements("port");

        for (Element port : ports) {
            String portName = port.attributeValue("name");

            Element address = (Element)port.elements().get(0);

            Attribute locationAttr = address.attribute("location");
            String newLocation =
                "local://" + service.workDir.getAbsolutePath() + "?" + portName;
            newLocation = newLocation.replace('\\', '/');
            locationAttr.setValue(newLocation);
        }

        // save file
        FileOutputStream os = new FileOutputStream(dest);
        new XMLWriter(os).write(doc);
        os.close();
    }

}
