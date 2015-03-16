/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.xml.ws.test;

import com.sun.xml.ws.test.container.WAR;
import com.sun.xml.ws.test.model.TestEndpoint;
import com.sun.xml.ws.test.util.FreeMarkerTemplate;
import com.sun.xml.ws.test.util.JavacTask;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class responsible for generation of bash scripts and java sources to
 * allow running ws-unit test(s) with plain java and bash only (no ws-harness)
 */
public class CodeGenerator {

    // generate sources or not ....
    private static boolean generateTestSources;

    // context
    public static int scriptOrder = 0;
    public static String id;

    private static String workDir;

    // scripts for one "testcase" (= 1 test-desriptor.xml)
    private static List<String> testcaseScripts = new ArrayList<String>();

    // all tests
    private static List<String> testcases = new ArrayList<String>();
    private static List<String> shutdownPortList = new ArrayList<String>();

    // if just one service is being deployed, it's always on 8080 port + endpoint stopper at 8888.
    // if multiple services deployed, we have to change port of second (and next ones) to avoid conflict
    // it is changed while being deployed (method fixPort)
    private static int deployedServices = 0;
    private static int freePort = 8080;
    private static Map<String, String> fixedServiceURLs = new HashMap<String, String>();

    public static void setGenerateTestSources(boolean generateTestSources) {
        CodeGenerator.generateTestSources = generateTestSources;
    }

    public static boolean isGenerateTestSources() {
        return generateTestSources;
    }

    public static void testCaseDone() {
        if (!generateTestSources) return;
        scriptOrder = 0;

        new FreeMarkerTemplate(id, scriptOrder, workDir, "shared").writeFileTo(workDir, "shared");

        FreeMarkerTemplate run = new FreeMarkerTemplate(id, scriptOrder, workDir, "run");
        run.put("scripts", toFilenames(testcaseScripts));
        run.put("shutdownPorts", shutdownPortList);
        String filename = run.writeFileTo(workDir, "run");
        testcases.add(filename);
        testcaseScripts.clear();

        FreeMarkerTemplate clean = new FreeMarkerTemplate(id, scriptOrder, workDir, "clean");
        clean.put("shutdownPorts", shutdownPortList);
        clean.writeFile();

        shutdownPortList.clear();
        fixedServiceURLs.clear();
        deployedServices = 0;
    }

    public static void allTestsDone(String dir) {
        if (!generateTestSources) return;
        FreeMarkerTemplate runall = new FreeMarkerTemplate(id, 0, chdir(dir), "runall");
        runall.put("testcases", testcases);
        runall.writeFileTo(chdir(dir), "/runall");
    }

    private static List<String> toFilenames(List<String> absolutePaths) {
        List<String> testcasesRelative = new ArrayList<String>();
        for (String s : absolutePaths) {
            testcasesRelative.add(s.substring(s.lastIndexOf('/') + 1));
        }
        return testcasesRelative;
    }

    private static List<String> toRelativePath(List<String> absolutePaths) {
        List<String> testcasesRelative = new ArrayList<String>();
        for (String s : absolutePaths) {
            testcasesRelative.add(toRelativePath(s));
        }
        return testcasesRelative;
    }

    public static void generateDeploy(Map<String, Object> params, String classpath, boolean fromwsdl) {
        if (!generateTestSources) return;
        if (workDir == null) return;

        //obsoleteDeploy(filename, classpath);

        FreeMarkerTemplate deploy = new FreeMarkerTemplate(id, scriptOrder, workDir, "deploy");
        classpath = chdir(classpath);
        classpath = toRelativePath(classpath);

        String serviceDirectory = classpath.replace(workDir, "").replaceAll("services/", "").replaceAll("war/WEB-INF/classes", "");
        deploy.put("serviceDirectory", serviceDirectory);

        // add also parent-parent dir in order to find resource "WEB-INF/wsdl/my.wsdl"
        // for @Provider services
        classpath = classpath + ":" + classpath.replaceAll("/WEB-INF/classes", "");
        deploy.put("classpath", classpath);

        // applicable if starting from wsdl - required to copy resources
        if (fromwsdl) {
            deploy.put("wsdlDocs", getWSDLDocs(params));
        }

        // applicable for annotations @Provider/wsdlLocation
        if (params.containsKey("wsdlLocation")) {
            deploy.put("wsdlLocation", params.get("wsdlLocation"));

            // deployment of provider requires jax-ws.xml descriptor;
            // lets 'steel' what harness generated
            copySunJaxwsXML(serviceDirectory);
        }

        String filename = deploy.writeFile();
        testcaseScripts.add(filename);

        // create dir
        File dir = new File(workDir + "/bsh");
        dir.mkdir();

        //obsoleteDeployCLass(contents, className);

        FreeMarkerTemplate utilClass = new FreeMarkerTemplate(id, scriptOrder, workDir, "bsh/Util.java");
        utilClass.writeFileTo(workDir + "/bsh", "Util.java");

        new File(workDir + "/junit").mkdir();
        new File(workDir + "/junit/framework").mkdir();
        FreeMarkerTemplate junitClass = new FreeMarkerTemplate(id, scriptOrder, workDir, "junit/framework/TestCase.java");
        junitClass.writeFileTo(workDir + "/junit/framework", "TestCase.java");

        FreeMarkerTemplate deployClass = new FreeMarkerTemplate(id, scriptOrder, workDir, "bsh/Deploy.java");
        for (String key : params.keySet()) {
            Object value = params.get(key);
            if (value instanceof List) {
                value = chdir((List<String>) value);
            } else if (value instanceof String) {
                value = chdir((String) value);
            }
            deployClass.put(key, value);
        }
        String port = "" + (8888 + deployedServices);
        shutdownPortList.add(port);
        deployClass.put("shutdownPort", port);
        deployClass.writeFileTo(workDir + "/bsh", "Deploy" + scriptOrder + ".java");

        scriptOrder++;
        deployedServices++;
    }

    protected static void copySunJaxwsXML(String serviceDirectory) {
        String source = workDir + "/services/" + serviceDirectory + "/war/WEB-INF/sun-jaxws.xml";
        source = source.replaceAll("testcases-no-harness", "testcases");
        String destination = workDir + "/../src/" + serviceDirectory + "/sun-jaxws.xml";
        try {
            SourcesCollector.copy(new File(source), new File(destination));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static List<String> getWSDLDocs(Map<String, Object> params) {
        List<String> list = chdir((List<String>) params.get("metadata_files"));
        List<String> result = new ArrayList<String>();
        String prefix = "WEB-INF/wsdl/";
        for (String file : list) {
            int pos = file.lastIndexOf(prefix) + prefix.length();
            result.add(file.substring(pos));
        }
        return result;
    }

    public static void generateClient(String classpath, String testName) {
        if (!generateTestSources) return;
        if (workDir == null) return;

        FreeMarkerTemplate client = new FreeMarkerTemplate(id, scriptOrder, workDir, "client");
        client.put("classpath", chdir(classpath));
        client.put("testName", testName);
        String filename = client.writeFile();
        testcaseScripts.add(filename);

        // create dir
        File dir = new File(workDir + "/bsh");
        dir.mkdir();

        scriptOrder++;
    }

    public static void startTestCase(String id, File workDir) {
        if (!generateTestSources) return;
        scriptOrder = 1;
        CodeGenerator.id = id;
        CodeGenerator.workDir = chdir(workDir.getAbsolutePath());
        String srcDir = new File(workDir.getAbsolutePath()).getParent();
        cleanDestDirectory(srcDir);
        SourcesCollector.ensureDirectoryExists(CodeGenerator.workDir);
        copySources(srcDir);
    }

    protected static void cleanDestDirectory(String srcDir) {
        String dstDir = chdir(srcDir);
        try {
            File f = new File(dstDir);
            if (f.exists()) {
                delete(f);
            }
        } catch (IOException e) {
            System.err.println("Error while cleaning dest dir.");
            e.printStackTrace();
        }
    }

    static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

    protected static void copySources(String srcDir) {
        SourcesCollector collector = new SourcesCollector(srcDir);
        collector.copyFilesTo(chdir(srcDir) + "/src");
    }

    // move everything out of (harness) testcases directory
    public static String chdir(String dir) {
        // to avoid multiple replacements ...
        dir = dir.replaceAll("/testcases-no-harness", "/testcases");
        return dir.replaceAll("/testcases", "/testcases-no-harness");
    }

    private static List<String> chdir(List<String> list) {
        List<String> changed = new ArrayList<String>();
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            s = chdir(s);
            changed.add(s);
        }
        return changed;
    }

    private static List<String> moveToSrc2(List<String> list) {
        List<String> changed = new ArrayList<String>();
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            s = CodeGenerator.moveToSrc2(s);
            changed.add(s);
        }
        return changed;
    }

    public static void generateJavac(JavacTask javac) {
        if (!generateTestSources) return;

        List<String> mkdirs = new ArrayList();
        String destDir = chdir(javac.getDestdir().toString());
        mkdirs.add(destDir);

        List<String> params = new ArrayList();
        params.add("-d " + toRelativePath(destDir));
        params.add("-cp " + toRelativePath(chdir(javac.getClasspath().toString())));

        for (String p : javac.getSrcdir().list()) {
            p = chdir(p);
            p = toRelativePath(moveToSrc(p));
            params.add("`find " + p + " -name '*.java'`");
            mkdirs.add(p);
        }

        FreeMarkerTemplate run = new FreeMarkerTemplate(id, scriptOrder, workDir, "javac");
        // TODO: how comes this is null?!
        run.put("mkdirs", toRelativePath(mkdirs));
        run.put("params", toRelativePath(params));
        String filename = run.writeFile();
        testcaseScripts.add(filename);
        scriptOrder++;
    }

    public static String moveToSrc(String directory) {
        File dir = new File(directory);
        File parent = dir.getParentFile();
        File testcaseDir = new File(workDir).getParentFile();

        // directory is same as testcase dir
        if (dir.equals(testcaseDir)) {
            return dir.toString() + "/src";
        }

        // directory is first level in testcase dir
        if (parent.equals(testcaseDir)) {
            return parent.toString() + "/src/" + dir.getName();
        }
        return directory;
    }

    public static String moveToSrc2(String directory) {
        if (directory == null || workDir == null) return directory;
        String workDirParent = new File(workDir).getParent();
        if (directory.startsWith(workDirParent) && !directory.contains("/work/")) {
            System.out.println("fixing directory = \n\t\t" + directory);
            directory = directory.replaceAll(workDirParent, workDirParent + "/src/");
            System.out.println("\t>>" + directory);
        }
        return directory;
    }

    public static void generateTool(List<String> dirsToBeCretaed, List<String> params) {
        if (!generateTestSources) return;

        FreeMarkerTemplate template = new FreeMarkerTemplate(id, scriptOrder, workDir, "tool");
        template.put("dirs", toRelativePath(moveToSrc2(chdir(dirsToBeCretaed))));
        template.put("params", moveToSrc2(chdir(params)));
        String filename = template.writeFile();
        testcaseScripts.add(filename);

        scriptOrder++;
    }

    public static void generateClientClass(
            String classpath,
            String testName,
            List<String> pImports,
            String pContents,
            Map<String, String> varMap) {

        if (!generateTestSources) return;

        // create dir
        File dir = new File(workDir + "/bsh");
        dir.mkdir();

        FreeMarkerTemplate clientClass = new FreeMarkerTemplate(id, scriptOrder, workDir, "bsh/Client.java");
        clientClass.put("classpath", classpath);
        clientClass.put("testName", testName);
        clientClass.put("pImports", pImports);
        clientClass.put("contents", pContents);
        for (String key : varMap.keySet()) {
            String value = varMap.get(key);
            value = fixedURL(value);
            clientClass.put(key, value);
        }
        clientClass.writeFileTo(workDir + "/bsh", "Client" + scriptOrder + ".java");
        generateClient(classpath, testName);
    }

    public static String fixPort(String value) {
        if (value.contains("http://localhost:8080")) {
            String fixed = value.replaceAll("http://localhost:8080", "http://localhost:" + getFreePort());
            fixedServiceURLs.put(value, fixed);
            return fixed;
        }
        return value;
    }

    public static int getFreePort() {
        return freePort + deployedServices;
    }

    public static void generateDeploySources(WAR war, TestEndpoint testEndpoint, List<Source> metadata,
                                             Map<String, Object> props, String endpointAddress,
                                             String wsdlLocation, boolean fromwsdl) {

        Map<String, Object> templateParams = new HashMap<String, Object>();

        // ugly hack:
        // in case explicit wsdlLocation (in java annotation) exists
        // ws-harness creates URLClassloader reading WEB-INF/wsdl + WEB-INF/classes to load service
        // let's fake it here ...
        if (wsdlLocation != null && wsdlLocation.length() > 0) {
            // location in no-harness directory structure; will be copied by deploy script
            templateParams.put("wsdlLocation", wsdlLocation.replaceAll("WEB-INF/wsdl/", ""));
        }

        List<String> metadata_files = new ArrayList<String>();
        for (Source source : metadata) {
            metadata_files.add(source.getSystemId().replaceAll("file:", ""));
        }
        templateParams.put("metadata_files", metadata_files);
        templateParams.put("props", props);

        QName qname = (QName) props.get("javax.xml.ws.wsdl.port");
        if (qname != null) {
            templateParams.put("portURI", "" + qname.getNamespaceURI());
            templateParams.put("portLOCAL", "" + qname.getLocalPart());
        }
        qname = (QName) props.get("javax.xml.ws.wsdl.service");
        if (qname != null) {
            templateParams.put("svcURI", "" + qname.getNamespaceURI());
            templateParams.put("svcLOCAL", "" + qname.getLocalPart());
        }
        templateParams.put("endpointImpl", "" + testEndpoint.className.replaceAll("\\$", "."));
        templateParams.put("endpointAddress", "" + CodeGenerator.fixPort(endpointAddress));
        generateDeploy(templateParams, war.classDir.getAbsolutePath(), fromwsdl);
    }

    // if the value is recognized as a client url, which was previously changed, it returns chnaged value,
    // otherwise returns original value
    public static String fixedURL(String value) {
        if (value.startsWith("http://")) {
            for (String partToBeFixed : fixedServiceURLs.keySet()) {
                if (value.contains(partToBeFixed)) {
                    String fixed = fixedServiceURLs.get(partToBeFixed);
                    return value.replaceAll(partToBeFixed, fixed);
                }
            }
        }
        return value;
    }

    public static String toRelativePath(String value) {
        if (!value.contains("http://")) {
            String fixed = chdir(value);
            fixed = fixed.replaceAll("//", "/");
            fixed = fixed.replaceAll(workDir + "/", "");
            fixed = fixed.replaceAll(workDir, "");
            String workDirParent = workDir.substring(0, workDir.lastIndexOf('/'));
            fixed = fixed.replaceAll(workDirParent, "..");
            return fixed;
        }
        return value;
    }
}
