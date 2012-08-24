/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.ws.test.model;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.istack.test.VersionProcessor;
import com.sun.istack.test.VersionNumber;
import com.sun.xml.ws.test.Main;
import com.sun.xml.ws.test.World;
import com.sun.xml.ws.test.client.InlineXmlResource;
import com.sun.xml.ws.test.client.ReferencedXmlResource;
import com.sun.xml.ws.test.client.ScriptBaseClass;
import com.sun.xml.ws.test.client.XmlResource;
import com.sun.xml.ws.test.container.ApplicationContainer;
import com.sun.xml.ws.test.container.DeployedService;
import com.sun.xml.ws.test.container.DeploymentContext;
import com.sun.xml.ws.test.exec.ClientCompileExecutor;
import com.sun.xml.ws.test.exec.ClientExecutor;
import com.sun.xml.ws.test.exec.ConcurrentClientExecutor;
import com.sun.xml.ws.test.exec.DeploymentExecutor;
import com.sun.xml.ws.test.exec.JavaClientExecutor;
import com.sun.xml.ws.test.exec.PrepareExecutor;
import com.sun.xml.ws.test.model.TransportSet.Singleton;
import com.sun.xml.ws.test.tool.WsTool;
import com.thaiopensource.relaxng.jarv.RelaxNgCompactSyntaxVerifierFactory;
import junit.framework.TestSuite;
import org.apache.tools.ant.types.FileSet;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.iso_relax.jaxp.ValidatingSAXParserFactory;
import org.iso_relax.verifier.Schema;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Root object of the test model. Describes one test.
 * <p/>
 * <p/>
 * TODO: Transaction needs some 'beforeTest'/'afterTest' hook to clean up database
 *
 * @author Kohsuke Kawaguchi
 */
public class TestDescriptor {

    /**
     * A Java identifier that represents a name of this test.
     * <p/>
     * <p/>
     * A test name needs to be unique among all the tests.  It will be
     * generated by the test harness dynamically from the test's partial
     * directory path. Namely, from toplevel test case directory to the
     * specific test case directory.
     * Something like this 'testcases.policy.parsing.someSpecificTest'
     * <p/>
     * This token is used by the harness to avoid collision when
     * running multiple tests in parallel (for example, this can be
     * used as a web application name so that multiple test services can be
     * deployed on the same container without interference.)
     */
    @NotNull
    public final String name;

    /**
     * If non-null, this directory contains resources files used for tests.
     * <p/>
     * <p/>
     * To clients, this resource will be available from {@link ScriptBaseClass#resource(String)}.
     */
    @Nullable
    public final File resources;

    /**
     * If non-null, this directory contains Java files shared by service and client.
     */
    @Nullable
    public final File common;


    /**
     * Versions of the program that this test applies.
     */
    @NotNull
    public final VersionProcessor applicableVersions;

    /**
     * Human readable description of this test.
     * This could be long text that spans across multiple lines.
     */
    @Nullable
    public final String description;

    /**
     * Represents a set of transport that this test supports.
     */
    @NotNull
    public final TransportSet supportedTransport;

    /**
     * Represents a set of Use keywords that are used by this test
     */
    public final Set<String> useSet;

    /**
     * Optional metadata files that describes this service.
     */
    public final List<String> metadatafiles = new ArrayList<String>();

    /**
     * Bugster IDs that are related to this test.
     * Can be empty set but not null.
     */
    @NotNull
    public final SortedSet<Integer> bugsterIds = new TreeSet<Integer>();

    /**
     * Client test scenarios that are to be executed.
     * <p/>
     * <p/>
     * When this field is empty, that means the test is just to make sure
     * that the service deploys.
     */
    @NotNull
    public final List<TestClient> clients = new ArrayList<TestClient>();

    /**
     * Possibly empty list of JAXB/JAX-WS external binding customizations.
     */
    @NotNull
    public final List<File> clientCustomizations = new ArrayList<File>();

    /**
     * Optional "set up" script executed before each client script.
     */
    @Nullable
    public final String setUpScript;

    /**
     * Java client.
     */
    @NotNull
    public final List<File> javaClients = new ArrayList<File>();

    /**
     * Services to be deployed for this test.
     */
    @NotNull
    public final List<TestService> services = new ArrayList<TestService>();

    /**
     * &lt;xml-resource>s.
     */
    public final Map<String, XmlResource> xmlResources = new HashMap<String, XmlResource>();

    /**
     * Root of the test data directory.
     */
    @NotNull
    public final File home;

    /**
     * Additional arguments to configure the harness behavior per test.
     */
    public final List<String> testOptions = new ArrayList<String>();


    /**
     * Additional command-line arguments to wsimport for generating client artifacts.
     */
    public final List<String> wsimportClientOptions = new ArrayList<String>();
    /**
     * Additional command-line arguments to wsimport for generating server artifacts
     * for Java-first case.
     */
    public final List<String> wsimportServerOptions = new ArrayList<String>();

    /**
     * Additional command-line arguments to wsgen for generating artifacts.
     */
    public final List<String> wsgenOptions = new ArrayList<String>();

    /**
     * Additional command-line arguments to javac
     */
    public final List<String> javacOptions = new ArrayList<String>();
    /**
     * Additional command-line arguments to javac
     */
    public final List<String> systemProperties = new ArrayList<String>();

    public static final Schema descriptorSchema;

    private boolean skip;

    private static final String JDK6_EXCLUDE_VERSION = "jdk6";

    /**
     * If true, we don't want to package the result of wsgen so that
     * we can test the generation of wrapper beans at the runtime.
     * <p/>
     * False otherwise.
     */
    public final boolean disgardWsGenOutput;

    public final boolean jdk6;

    static {
        URL url = World.class.getResource("test-descriptor.rnc");
        try {
            descriptorSchema = new RelaxNgCompactSyntaxVerifierFactory().compileSchema(url.toExternalForm());
        } catch (SAXParseException e) {
            throw new Error("unable to parse test-descriptor.rnc at line " + e.getLineNumber(), e);
        } catch (Exception e) {
            throw new Error("unable to parse test-descriptor.rnc", e);
        }
    }


    public TestDescriptor(String shortName, File home, File resources, File common, VersionProcessor applicableVersions, String description, boolean disgardWsGenOutput, boolean jdk6) {
        this.name = shortName;
        this.home = home;
        this.resources = resources;
        this.common = common;
        this.applicableVersions = applicableVersions;
        this.disgardWsGenOutput = disgardWsGenOutput;
        this.supportedTransport = TransportSet.ALL;
        this.description = description;
        this.skip = false;
        this.setUpScript = null;
        this.jdk6 = jdk6;
        this.useSet = new HashSet<String>();
    }

    /**
     * Parses a {@link TestDescriptor} from a test data directory.
     *
     * @param descriptor Test descriptor XML file.
     */
    public TestDescriptor(File descriptor, boolean disgardWsGenOutput, boolean jdk6) throws IOException, DocumentException, ParserConfigurationException, SAXException {
        this.disgardWsGenOutput = disgardWsGenOutput;
        this.jdk6 = jdk6;
        File testDir = descriptor.getParentFile();
        Element root = parse(descriptor).getRootElement();

        VersionProcessor versionProcessor;
        this.description = root.elementTextTrim("description");
        /*
         * Check if the resources folder exists in the dir where the
         * test-descriptor.xml is present else it is null
         */
        File resourceDir = new File(testDir, "resources");
        this.resources = resourceDir.exists() ? resourceDir : null;

        for (Element xre : (List<Element>) root.elements("xml-resource")) {
            final XmlResource xr;
            if (xre.attribute("href") == null)
                xr = new InlineXmlResource((Element) xre.elements().get(0));
            else
                xr = new ReferencedXmlResource(new File(testDir, xre.attributeValue("href")));
            xmlResources.put(xre.attributeValue("name"), xr);
        }


        /*
         * Check if the common folder exists in the dir where the
         * test-descriptor.xml is present else it is null
         */
        File commonDir = new File(testDir, "common");
        this.common = commonDir.exists() ? commonDir : null;
        this.applicableVersions = getVersionProcessor(root);

        this.skip = Boolean.parseBoolean(root.attributeValue("skip"));

        parseArguments(root.elementText("test-options"), testOptions);
        parseArguments(root.elementText("wsimport-client"), wsimportClientOptions);
        parseArguments(root.elementText("wsimport-server"), wsimportServerOptions);
        parseArguments(root.elementText("wsgen-options"), wsgenOptions);
        parseArguments(root.elementText("javac-options"), javacOptions);
        parseArguments(root.elementText("system-properties"), systemProperties);

        String transport = root.attributeValue("transport");
        if (transport == null)
            this.supportedTransport = TransportSet.ALL;
        else
            this.supportedTransport = new Singleton(transport);

        this.useSet = new HashSet<String>();
        String uses = root.attributeValue("uses");
        if (uses != null) {
            StringTokenizer st = new StringTokenizer(uses);
            while (st.hasMoreTokens()) {
                useSet.add(st.nextToken());
            }
        }

        String path = testDir.getCanonicalPath();
        String testCasesPattern = "testcases" + File.separatorChar;
        int testCaseIndex = path.lastIndexOf(testCasesPattern);
        testCaseIndex += testCasesPattern.length();
        /*
         * For something like this 'testcases.policy.parsing.someSpecificTest'
         * I think the shortName should be policy.parsing
         * not testcases.policy.parsing as the above would conform to
         * a valid package name too
         */
        this.name = path.substring(testCaseIndex).replace(File.separatorChar, '.');

        this.home = descriptor.getParentFile();

        this.setUpScript = root.elementText("pre-client");

        List<Element> clientList = root.elements("client");
        for (Element client : clientList) {
            versionProcessor = getVersionProcessor(client);

            boolean sideEffectFree = client.attribute("sideEffectFree") != null;
            String clientTransport = client.attributeValue("transport");
            TransportSet clientSupportedTransport = (clientTransport == null)
                    ? TransportSet.ALL : new Singleton(clientTransport);


            if (client.attribute("href") != null) {
                // reference to script files
                FileSet fs = new FileSet();
                fs.setDir(testDir);
                fs.setIncludes(client.attributeValue("href"));
                for (String relPath : fs.getDirectoryScanner(World.project).getIncludedFiles()) {
                    TestClient testClient = new TestClient(this, versionProcessor, clientSupportedTransport,
                            new Script.File(new File(testDir, relPath)), sideEffectFree);
                    this.clients.add(testClient);
                }
            } else {
                // literal text
                TestClient testClient = new TestClient(this, versionProcessor, clientSupportedTransport,
                        new Script.Inline(client.attributeValue("name"), client.getText()),
                        sideEffectFree);
                this.clients.add(testClient);
            }
        }

        File customization = parseFile(testDir, "custom-client.xml");
        if (customization.exists())
            clientCustomizations.add(customization);
        File schemaCustomization = parseFile(testDir, "custom-schema-client.xml");
        if (schemaCustomization.exists())
            clientCustomizations.add(schemaCustomization);

        findAllJavaClients(home);


        List<Element> serviceList = root.elements("service");
        populateServices(serviceList, testDir, false);
        List<Element> stsList = root.elements("sts");
        populateServices(stsList, testDir, true);


        List<Element> elements = root.elements("external-metadata");
        if (elements != null) {
            for(Element element : elements) {
                Attribute fileAttr = element.attribute("file");
                if (fileAttr != null) {
                    String filepath = fileAttr.getValue();
                    metadatafiles.add(filepath);
                }
            }
        }

    }

    /**
     * Tokenize the given string and add them to the given list.
     */
    private void parseArguments(String s, List<String> result) {
        if (s != null)
            result.addAll(Arrays.asList(s.split("\\p{Space}+")));
    }

    /**
     * Recursively scans the test directory and finds all the Java test files.
     */
    private void findAllJavaClients(File dir) {
        for (File child : dir.listFiles()) {
            if (child.getName().equals("work"))
                // don't look for the generated files, since often JAXB generates
                // files that end with 'Test' from WSDL
                continue;
            if (child.isDirectory())
                findAllJavaClients(child);
            if (child.getName().endsWith("Test.java"))
                javaClients.add(child);
        }
    }


    /**
     * Creates the execution plan of this test descriptor and adds them
     * to {@link TestSuite} (so that when {@link TestSuite}
     * is executed, you execute this test.
     *
     * @param container                The container to host the services.
     * @param clientScriptName         See {@link Main#clientScriptName}
     * @param concurrentSideEffectFree See {@link Main#concurrentSideEffectFree}
     * @return {@link TestSuite} that contains test execution plan for this test.
     */
    public TestSuite build(ApplicationContainer container, WsTool wsimport, String clientScriptName,
                           boolean concurrentSideEffectFree, VersionNumber version) throws IOException {

        TestSuite suite = new TestSuite();

        if (skip) {
            System.out.println("Skipping " + name + "; explictly marked to skip.");
            return suite;
        }

        if (!supportedTransport.contains(container.getTransport())) {
            System.out.println("Skipping " + name + " as it's not applicable to " + container.getTransport());
            return suite;
        }

        Set<String> temp = new HashSet<String>(useSet);
        temp.retainAll(container.getUnsupportedUses());
        if (temp.size() > 0) {
            System.out.println("Skipping " + name + " as the container " + container + " doesn't support " + temp);
            return suite;
        }

        DeploymentContext context = new DeploymentContext(this, container, wsimport);

        List<DeploymentExecutor> deployTests = new ArrayList<DeploymentExecutor>();

        // first prepare the working directories.
        // we shouldn't do it after the run, or else developers won't be able to
        // see what's generated to debug problems
        // in the -skip mode, don't clean
        suite.addTest(new PrepareExecutor(context, !wsimport.isNoop()));

        // deploy all services
        for (DeployedService s : context.services.values()) {
            DeploymentExecutor dt = new DeploymentExecutor(s);
            deployTests.add(dt);
            suite.addTest(dt);
        }

        if (context.services.isEmpty() && new File(context.descriptor.home, "client").exists()) {
            // no services. just run the clients as tests
            suite.addTest(new ClientCompileExecutor(context));
        }

        // run client test scripts
        for (TestClient c : clients) {
            if (clientScriptName != null && !c.script.getName().equals(clientScriptName))
                continue; // skip
            if (version != null && !c.applicableVersions.isApplicable(version)) {
                System.err.println("Not applicable to current version=" + version + ". Skipping " + c.script.getName());
                continue;
            }
            if (!c.supportedTransport.contains(container.getTransport())) {
                System.out.println("Skipping " + c.script.getName() + " as it's not applicable to " + container.getTransport());
                continue;
            }
            if (concurrentSideEffectFree && c.sideEffectFree) {
                suite.addTest(new ConcurrentClientExecutor.Fixed(context, c));
                suite.addTest(new ConcurrentClientExecutor.Cached(context, c));
            } else
                suite.addTest(new ClientExecutor(context, c));
        }

        // run client Java tests
        for (File f : javaClients)
            suite.addTest(new JavaClientExecutor(context, f, version));

        // undeploy all services
        for (DeploymentExecutor dt : deployTests) {
            suite.addTest(dt.createUndeployer());
        }

        return suite;
    }

    /**
     * Parses a potentially relative file path.
     */
    private File parseFile(File base, String href) {

        File f = new File(href);
        if (f.isAbsolute())
            return f;
        else
            return new File(base, href);
    }

    /**
     * Parses a test descriptor.
     */
    private Document parse(File descriptor) throws DocumentException, SAXException, ParserConfigurationException {
        SAXParserFactory factory;
        if (descriptorSchema != null) {
            factory = new ValidatingSAXParserFactory(descriptorSchema);
        } else {
            factory = SAXParserFactory.newInstance();
        }
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        return new SAXReader(factory.newSAXParser().getXMLReader()).read(descriptor);
    }

    /**
     * Returns a human readable name that identifies the test,
     * for better readability of the test result report.
     */
    public String toString() {
        return name;
    }

    class XSDFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return (name.endsWith(".xsd"));
        }
    }

    /**
     * This filter gives all wsdls in the directory excluding primaryWsdl.
     * This can be used to gather all imported wsdls.
     */

    class WSDLFilter implements FilenameFilter {
        String primaryWsdl;

        public WSDLFilter(String primaryWsdl) {
            this.primaryWsdl = primaryWsdl;
        }

        public boolean accept(File dir, String name) {
            return (name.endsWith(".wsdl") && (!name.equals(primaryWsdl)));
        }
    }

    private void populateServices(List<Element> serviceList, File testDir, boolean isSTS) throws IOException {
        for (Element service : serviceList) {
            String baseDir = service.attributeValue("basedir", ".");

            String serviceName;
            File serviceBaseDir;
            if (!baseDir.equals(".")) {
                serviceBaseDir = new File(testDir, baseDir);
                serviceName = serviceBaseDir.getCanonicalFile().getName();
            } else {
                serviceName = "";
                serviceBaseDir = testDir;
            }

            File wsdl;
            WSDL wsdlInfo = null;
            if (service.element("wsdl") != null) {
                String wsdlAttribute = service.element("wsdl").attributeValue("href", "test.wsdl");
                wsdl = parseFile(serviceBaseDir, wsdlAttribute);

                FileSet schemaSet = new FileSet();
                schemaSet.setDir(serviceBaseDir);
                schemaSet.setIncludes("**/*.xsd");
                schemaSet.setExcludes("work/**");
                List<File> schemaFiles = new ArrayList<File>();
                for (String relPath : schemaSet.getDirectoryScanner(World.project).getIncludedFiles()) {
                    schemaFiles.add(new File(serviceBaseDir, relPath));
                }

                FileSet wsdlSet = new FileSet();
                wsdlSet.setDir(serviceBaseDir);
                wsdlSet.setIncludes("**/*.wsdl");
                wsdlSet.setExcludes("wsdlAttribute, work/**");

                List<File> importedWsdls = new ArrayList<File>();
                for (String relPath : wsdlSet.getDirectoryScanner(World.project).getIncludedFiles()) {
                    importedWsdls.add(new File(serviceBaseDir, relPath));
                }
//                File[] schemas = serviceBaseDir.listFiles(new XSDFilter());
//                File[] wsdls = serviceBaseDir.listFiles(new WSDLFilter(wsdlAttribute));
//                List<File> importedWsdls = Arrays.asList(wsdls);
//                List<File> schemaFiles = Arrays.asList(schemas);
                wsdlInfo = new WSDL(wsdl, importedWsdls, schemaFiles);

            }

            TestService testService = new TestService(this, serviceName, serviceBaseDir, wsdlInfo, isSTS,
                    service.attributeValue("class"));

            File customization = parseFile(serviceBaseDir, "custom-server.xml");
            if (customization.exists()) {
                testService.customizations.add(customization);
            }
            File schemaCustomization = parseFile(serviceBaseDir, "custom-schema-server.xml");
            if (schemaCustomization.exists()) {
                testService.customizations.add(schemaCustomization);
            }


            this.services.add(testService);

        }
    }

    private VersionProcessor getVersionProcessor(Element e) {
        // <client excludeFrom="jdk6" is excluded when run with -jdk6 flag
        String excludeFrom = e.attributeValue("excludeFrom", null);
        if (excludeFrom != null && excludeFrom.contains(JDK6_EXCLUDE_VERSION)) {
            if (jdk6) {
                excludeFrom = "all";
            } else {
                excludeFrom = excludeFrom.replace(JDK6_EXCLUDE_VERSION, "");
            }
        }
        return new VersionProcessor(
                e.attributeValue("since", null),
                e.attributeValue("until", null),
                excludeFrom);
    }
}
