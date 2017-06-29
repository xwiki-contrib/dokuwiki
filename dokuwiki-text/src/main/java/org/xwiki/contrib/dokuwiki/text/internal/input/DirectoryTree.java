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

class DirectoryTree
{
    private Folder root;
    private Folder commonRoot;

    DirectoryTree(Folder root)
    {
        this.root = root;
        commonRoot = null;
    }

    void addElement(String elementValue, Boolean isFolder)
    {
        String[] list = elementValue.split("/");
        // last element of the list is the filename.extrension
        root.addElement(root.getIncrementalPath(), list, isFolder);
    }

    Folder getCommonRoot()
    {
        if (commonRoot != null) {
            return commonRoot;
        } else {
            Folder current = root;
            while (current.getLeafs().size() <= 0 && current.getChilds().size() <= 1)
            {
                current = current.getChilds().get(0);
            }
            commonRoot = current;
            return commonRoot;
        }
    }
}
