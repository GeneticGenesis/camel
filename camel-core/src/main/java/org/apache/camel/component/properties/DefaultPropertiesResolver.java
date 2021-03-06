/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.properties;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Default {@link org.apache.camel.component.properties.PropertiesResolver} which can resolve properties
 * from file and classpath.
 * <p/>
 * You can denote <tt>classpath:</tt> or <tt>file:</tt> as prefix in the uri to select whether the file
 * is located in the classpath or on the file system.
 *
 * @version 
 */
public class DefaultPropertiesResolver implements PropertiesResolver {

    public Properties resolveProperties(CamelContext context, boolean ignoreMissingLocation, String... uri) throws Exception {
        Properties answer = new Properties();

        for (String path : uri) {
            if (path.startsWith("ref:")) {
                Properties prop = loadPropertiesFromRegistry(context, ignoreMissingLocation, path);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
            } else if (path.startsWith("file:")) {
                Properties prop = loadPropertiesFromFilePath(context, ignoreMissingLocation, path);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
            } else {
                // default to classpath
                Properties prop = loadPropertiesFromClasspath(context, ignoreMissingLocation, path);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
            }
        }

        return answer;
    }

    protected Properties loadPropertiesFromFilePath(CamelContext context, boolean ignoreMissingLocation, String path) throws IOException {
        Properties answer = new Properties();

        if (path.startsWith("file:")) {
            path = ObjectHelper.after(path, "file:");
        }

        InputStream is = null;
        try {
            is = new FileInputStream(path);
            answer.load(is);
        } catch (FileNotFoundException e) {
            if (!ignoreMissingLocation) {
                throw e;
            }
        } finally {
            IOHelper.close(is);
        }

        return answer;
    }

    protected Properties loadPropertiesFromClasspath(CamelContext context, boolean ignoreMissingLocation, String path) throws IOException {
        Properties answer = new Properties();

        if (path.startsWith("classpath:")) {
            path = ObjectHelper.after(path, "classpath:");
        }

        InputStream is = context.getClassResolver().loadResourceAsStream(path);
        if (is == null) {
            if (!ignoreMissingLocation) {
                throw new FileNotFoundException("Properties file " + path + " not found in classpath");
            }
        } else {
            try {
                answer.load(is);
            } finally {
                IOHelper.close(is);
            }
        }
        return answer;
    }

    protected Properties loadPropertiesFromRegistry(CamelContext context, boolean ignoreMissingLocation, String path) throws IOException {
        if (path.startsWith("ref:")) {
            path = ObjectHelper.after(path, "ref:");
        }
        Properties answer = context.getRegistry().lookup(path, Properties.class);
        if (answer == null && (!ignoreMissingLocation)) {
            throw new FileNotFoundException("Properties " + path + " not found in registry");
        }
        return answer != null ? answer : new Properties();
    }

    /**
     * Strategy to prepare loaded properties before being used by Camel.
     * <p/>
     * This implementation will ensure values are trimmed, as loading properties from
     * a file with values having trailing spaces is not automatic trimmed by the Properties API
     * from the JDK.
     *
     * @param properties  the properties
     * @return the prepared properties
     */
    protected Properties prepareLoadedProperties(Properties properties) {
        Properties answer = new Properties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;

                // trim any trailing spaces which can be a problem when loading from
                // a properties file, note that java.util.Properties does already this
                // for any potential leading spaces so there's nothing to do there
                value = trimTrailingWhitespaces(s);
            }
            answer.put(key, value);
        }
        return answer;
    }

    private static String trimTrailingWhitespaces(String s) {
        int endIndex = s.length();
        for (int index = s.length() - 1; index >= 0; index--) {
            if (s.charAt(index) == ' ') {
                endIndex = index;
            } else {
                break;
            }
        }
        String answer = s.substring(0, endIndex);
        return answer;
    }

}
