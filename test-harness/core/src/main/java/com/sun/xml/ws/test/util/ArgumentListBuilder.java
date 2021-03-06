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

package com.sun.xml.ws.test.util;

import com.sun.xml.ws.test.tool.WsTool;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provide convenient methods for building up command-line arguments.
 *
 * <p>
 * This class can be used in a chained-invocation style like {@link StringBuilder}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ArgumentListBuilder {
    private final List<String> args = new ArrayList<String>();

    public ArgumentListBuilder add(String token) {
        args.add(token);
        return this;
    }

    /**
     * Adds a file path as an argument.
     */
    public ArgumentListBuilder add(File path) {
        return add(path.getAbsoluteFile().getPath());
    }

    public ArgumentListBuilder add(URL path) {
        return add(path.toExternalForm());
    }

    /**
     * Invokes the tool with arguments built so far.
     */
    public void invoke(WsTool tool) throws Exception {
        tool.invoke(args.toArray(new String[args.size()]));
    }

    public ArgumentListBuilder add(Path cp) {
        return add(cp.toString());
    }

    public ArgumentListBuilder addAll(Collection<String> values) {
        args.addAll(values);
        return this;
    }


    public String toString() {
        return args.toString();
    }
}
