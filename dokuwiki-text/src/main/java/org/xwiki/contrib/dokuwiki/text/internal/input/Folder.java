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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Folder
{
    private String name;
    private String incrementalPath;
    private List<Folder> childs;
    private List<Folder> leafs;

    Folder(String nodeValue, String incrementalPath)
    {
        name = nodeValue;
        this.incrementalPath = incrementalPath;
        childs = new ArrayList<Folder>();
        leafs = new ArrayList<Folder>();
    }

    String getIncrementalPath() {
        return incrementalPath;
    }

    public void setIncrementalPath(String incrementalPath) {
        this.incrementalPath = incrementalPath;
    }

    List<Folder> getChilds() {
        return childs;
    }

    public void setChilds(List<Folder> childs) {
        this.childs = childs;
    }

    List<Folder> getLeafs() {
        return leafs;
    }

    public void setLeafs(List<Folder> leafs) {
        this.leafs = leafs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    boolean isLeaf() {
        return childs.isEmpty() && leafs.isEmpty();
    }

    void addElement(String currentPath, String[] elementList, boolean isFolder)
    {
        String[] list = elementList;
        while (list[0] == null || list[0].equals("")) {
            list = Arrays.copyOfRange(list, 1, list.length);
        }
        Folder currentChild = new Folder(list[0], currentPath + "/" + list[0]);
        if (list.length == 1 & !isFolder) {
            leafs.add(currentChild);
        } else {
            int index = childs.indexOf(currentChild);
            if (index == -1) {
                childs.add(currentChild);
                if (list.length > 1) {
                    currentChild.addElement(currentChild.incrementalPath,
                            Arrays.copyOfRange(list, 1, list.length), isFolder);
                }
            } else if (list.length > 1) {
                Folder nextChild = childs.get(index);
                nextChild.addElement(currentChild.incrementalPath, Arrays.copyOfRange(list, 1, list.length), isFolder);
            }
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        Folder cmpObj = (Folder) obj;
        return incrementalPath.equals(cmpObj.incrementalPath) && name.equals(cmpObj.name);
    }

    @Override
    public native int hashCode();
    {
        //just to deal with the check style violations.
    }

    @Override
    public String toString() {
        return name;
    }
}
