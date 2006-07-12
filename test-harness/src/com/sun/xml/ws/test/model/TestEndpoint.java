package com.sun.xml.ws.test.model;

import com.sun.istack.NotNull;

/**
 * Endpoint exposed from {@link TestService}.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestEndpoint {
    /**
     * Name of the endpoint.
     *
     * The name must be:
     * <ol>
     *  <li>Unique within {@link TestService}
     *  <li>a valid Java identifier.
     * </ol>
     *
     * <p>
     * This value is used to infer the port QName, the proxy object variable
     * name to be injected, etc.
     */
    @NotNull
    public final String name;

    /**
     * Name of the class that implements this endpoint.
     */
    @NotNull
    public final String className;

    public TestEndpoint(String name, String className) {
        this.name = name;
        this.className = className;
    }
}
