package com.sun.xml.ws.test;

import org.apache.tools.ant.Project;
import org.codehaus.classworlds.ClassRealm;

import java.util.Properties;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * "Global variables" for the test harness. Use with caution.
 *
 * This class includes pointers to
 * various {@link ClassRealm}s that represent compartments inside the VM.
 *
 * <p>
 * The followings are the key realms:
 *
 * <ol>
 * <li>"harness" realm that loads all the test harness code,
 *     including lots of 3rd party jars.
 * <li>"runtime" realm that loads the classes that the client script will use
 *     to execute tests.
 * <li>"wsimport" realm that loads the tool/wsgen tools, if we invoke it
 *     within the same VM. Otherwise this realm is empty.
 *
 * <p>
 * Realms are created when {@link World} is created, but they are filled in
 * from {@link Main}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class World {
    /**
     * Whenever we need a {@link Project} to use Ant tasks,
     * we can use this shared instance.
     */
    public static final Project project = new Project();

    static {
        // trying to fix NPE in Project.init()
        Properties systemP = System.getProperties();
        for (Iterator<Entry<Object, Object>> itr = systemP.entrySet().iterator(); itr.hasNext();) {
            Entry<Object, Object> entry =  itr.next();
            if(entry.getValue()==null)
                itr.remove();
        }

        project.init();
    }

    /**
     * Loads JAX-WS runtime classes.
     *
     * This realm is also used to load the embedded application container,
     * so that we don't have to package JAX-WS runtime into the war file.
     */
    public static final Realm runtime = new Realm("runtime",null);
    public static final Realm tool    = new Realm("tool",   runtime);

    /**
     * @see Main#debug
     */
    public static boolean debug = false;
}
