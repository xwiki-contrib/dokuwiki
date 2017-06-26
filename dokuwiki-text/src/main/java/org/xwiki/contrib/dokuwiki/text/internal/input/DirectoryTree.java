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
package org.xwiki.contrib.dokuwiki.text.internal.input;

import org.xwiki.filter.input.DirectoryInputSource;

/**
 * Created by shubhamjain on 26/06/17.
 */
public class DirectoryTree {
    Folder root;
    Folder commonRoot;

    public DirectoryTree( Folder root ) {
        this.root = root;
        commonRoot = null;
    }

    public void addElement( String elementValue, Boolean isFolder) {
        String[] list = elementValue.split("/");
        // last element of the list is the filename.extrension
        root.addElement(root.incrementalPath, list, isFolder);
    }

    public Folder getCommonRoot() {
        if ( commonRoot != null)
            return commonRoot;
        else {
            Folder current = root;
            while ( current.leafs.size() <= 0 ) {
                current = current.childs.get(0);
            }
            commonRoot = current;
            return commonRoot;
        }
    }
}
