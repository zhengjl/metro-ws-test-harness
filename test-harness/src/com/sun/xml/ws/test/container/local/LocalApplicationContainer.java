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

package com.sun.xml.ws.test.container.local;

import com.sun.istack.NotNull;
import com.sun.xml.ws.test.container.AbstractApplicationContainer;
import com.sun.xml.ws.test.container.Application;
import com.sun.xml.ws.test.container.ApplicationContainer;
import com.sun.xml.ws.test.container.DeployedService;
import com.sun.xml.ws.test.container.WAR;
import com.sun.xml.ws.test.model.TestEndpoint;
import com.sun.xml.ws.test.tool.WsTool;
import java.io.FileWriter;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.List;

/**
 * {@link ApplicationContainer} for the local transport.
 *
 * @deprecated
 *      To be removed once in-vm transport becomes ready
 * @author Kohsuke Kawaguchi
 */
public class LocalApplicationContainer extends AbstractApplicationContainer {

    public LocalApplicationContainer(WsTool wsimport, WsTool wsgen) {
        super(wsimport,wsgen);
    }

    public String getTransport() {
        return "local";
    }

    public void start() {
        // noop
    }

    public void shutdown() {
        // noop
    }

    @NotNull
    public Application deploy(DeployedService service) throws Exception {
        WAR war = assembleWar(service);
        if (service.service.isSTS)
            updateWsitClient(service);
        for (File wsdl : war.getWSDL())
            patchWsdl(service,wsdl);
        return new LocalApplication(war,new URI("local://" +
            service.warDir.getAbsolutePath().replace('\\','/')));
    }

    public void updateWsitClient(DeployedService deployedService)throws Exception {
        File wsitClientFile = new File(deployedService.service.parent.resources,"wsit-client.xml");
        if (wsitClientFile.exists() ){
            SAXReader reader = new SAXReader();
            Document document = reader.read(wsitClientFile);
            Element root = document.getRootElement();
            Element policy = root.element("Policy");
            Element sts = policy.element("ExactlyOne").element("All").element("PreconfiguredSTS");

            Attribute  endpoint = sts.attribute("endpoint");
            TestEndpoint foo = (TestEndpoint) deployedService.service.endpoints.toArray()[0];
            String newLocation =
                "local://" + deployedService.warDir.getAbsolutePath() + "/";
            newLocation = newLocation.replace('\\', '/');
            endpoint.setValue(newLocation);

            Attribute wsdlLoc = sts.attribute("wsdlLocation");
            wsdlLoc.setValue(deployedService.service.wsdl.wsdlFile.toURI().toString());

            XMLWriter writer = new XMLWriter(new FileWriter(wsitClientFile));
            writer.write( document );
            writer.close();

        } else {
            throw new RuntimeException("wsit-client.xml is absent. It is required. \n"+
                    "Please check " + deployedService.service.parent.resources );
        }
    }

    /**
     * Fix the address in the WSDL. to the local address.
     */
    private void patchWsdl(DeployedService service, File wsdl) throws Exception {
        Document doc = new SAXReader().read(wsdl);
        List<Element> ports = doc.getRootElement().element("service").elements("port");

        for (Element port : ports) {
            String portName = port.attributeValue("name");

            Element address = (Element)port.elements().get(0);

            Attribute locationAttr = address.attribute("location");
            String newLocation =
                "local://" + service.warDir.getAbsolutePath() + "?" + portName;
            newLocation = newLocation.replace('\\', '/');
            locationAttr.setValue(newLocation);
        }

        // save file
        FileOutputStream os = new FileOutputStream(wsdl);
        new XMLWriter(os).write(doc);
        os.close();
    }

}
