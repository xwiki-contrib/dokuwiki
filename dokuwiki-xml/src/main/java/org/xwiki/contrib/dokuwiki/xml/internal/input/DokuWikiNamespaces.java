/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.dokuwiki.xml.internal.input;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Helper to manipulate DokuWiki namespaces.
 * <p>
 * See https://www.dokuwiki.org/wiki/Manual:Namespace for more details.
 * 
 * @version $Id$
 * @since 1.8
 */
public class DokuWikiNamespaces
{
    /**
     * The default name of the file namespace.
     */
    public static final String NAMESPACE_FILE_DEFAULT = "File";

    /**
     * The index of the file namespace.
     */
    public static final int NAMESPACE_FILE_IDX = 6;

    /**
     * The default name of the user namespace.
     */
    public static final String NAMESPACE_USER_DEFAULT = "User";

    /**
     * The index of the user namespace.
     */
    public static final int NAMESPACE_USER_IDX = 2;

    /**
     * The default name of the spacial namespace.
     */
    public static final String NAMESPACE_SPECIAL_DEFAULT = "Special";

    /**
     * The index of the special namespace.
     */
    public static final int NAMESPACE_SPECIAL_IDX = -1;

    private final Map<Integer, Collection<String>> keyToNamespaces = new HashMap<>();

    private final Map<String, Integer> namespaceToKey = new HashMap<>();

    private final Map<Integer, String> defaultNamespace = new HashMap<>();

    /**
     * Default constructor.
     */
    public DokuWikiNamespaces()
    {
        // Initialize with standard namespaces.
        // See https://www.dokuwiki.org/wiki/Manual:Namespace#Built-in_namespaces
        addNamespace(0, "");
        addNamespace(NAMESPACE_USER_IDX, NAMESPACE_USER_DEFAULT);
        addNamespace(4, "Project");
        addNamespace(NAMESPACE_FILE_IDX, NAMESPACE_FILE_DEFAULT);
        addNamespace(NAMESPACE_FILE_IDX, "Image");
        addNamespace(8, "DokuWiki");
        addNamespace(10, "Template");
        addNamespace(12, "Help");
        addNamespace(14, "Category");

        addNamespace(NAMESPACE_SPECIAL_IDX, NAMESPACE_SPECIAL_DEFAULT);
        addNamespace(-2, "Doku");
    }

    /**
     * @param key the key associated to the namespace
     * @param namespace the namespace
     */
    public void addNamespace(String key, String namespace)
    {
        addNamespace(Integer.valueOf(key), namespace);
    }

    /**
     * @param key the key associated to the namespace
     * @param namespace the namespace
     */
    public void addNamespace(int key, String namespace)
    {
        Collection<String> namespaces = this.keyToNamespaces.get(key);

        if (namespaces == null) {
            namespaces = new HashSet<>();
            namespaces.add(namespace);
            this.keyToNamespaces.put(key, namespaces);
        }

        namespaces.add(namespace);

        this.namespaceToKey.put(namespace.toLowerCase(), key);
        this.defaultNamespace.put(key, namespace);
    }

    /**
     * @param name the name to test
     * @return if the passed namespace is a registered namespace
     */
    public boolean isNamespace(String name)
    {
        return this.namespaceToKey.containsKey(name.toLowerCase());
    }

    /**
     * @param key the namespace key
     * @param namespace the namespace name
     * @return true if the passed namespace name is associated to the passed namespace key
     */
    public boolean isNamespace(int key, String namespace)
    {
        Integer standardKey = this.namespaceToKey.get(namespace.toLowerCase());

        return standardKey != null && standardKey.intValue() == key;
    }

    /**
     * @param namespace the namespace to test
     * @return true if the passed namespace is an alias of file namespace
     */
    public boolean isFileNamespace(String namespace)
    {
        return isNamespace(NAMESPACE_FILE_IDX, namespace);
    }

    /**
     * @return the default namespace used as file namespace alias
     */
    public String getFileNamespace()
    {
        return this.defaultNamespace.get(NAMESPACE_FILE_IDX);
    }

    /**
     * @param name the name to resolve
     * @return the registered default name corresponding to the passed namespace
     */
    public String resolve(String name)
    {
        Integer key = this.namespaceToKey.get(name.toLowerCase());
        if (key != null) {
            return this.defaultNamespace.get(key);
        }

        return name;
    }

    /**
     * @param key the key of the namespace
     * @return the defualt name for the passed namespace
     */
    public String getDefaultNamespace(int key)
    {
        return this.defaultNamespace.get(key);
    }

    /**
     * @param title the page title to parse
     * @return the file name
     */
    public String getFileName(String title)
    {
        for (String namespace : this.keyToNamespaces.get(NAMESPACE_FILE_IDX)) {
            if (StringUtils.startsWithIgnoreCase(title, namespace + ':')) {
                return title.substring(namespace.length() + 1).replace(' ', '_');
            }
        }

        return null;
    }

    /**
     * @param title the reference to parse
     * @return true if the passed reference belong to a file
     */
    public boolean isInFileNamespace(String title)
    {
        return getFileName(title) != null;
    }

    /**
     * @param namespace the namespace to test
     * @return true if the passed namespace is an alias of special namespace
     */
    public boolean isSpecialNamespace(String namespace)
    {
        return isNamespace(NAMESPACE_SPECIAL_IDX, namespace);
    }

    /**
     * @return the namespaces
     */
    public Map<Integer, Collection<String>> getNamespaces()
    {
        return this.keyToNamespaces;
    }
}
