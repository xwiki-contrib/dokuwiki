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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.LocalDocumentReference;

/**
 * Various converter methods for DokuWiki references.
 *
 * @version $Id$
 * @since 2.0
 */
@Component(roles = DokuWikiReferenceConverter.class)
@Singleton
public class DokuWikiReferenceConverter
{
    private static final String DOKUWIKI_START_PAGE = "start";

    private static final String XWIKI_START_PAGE = "WebHome";

    private static final String XWIKI_MAIN_SPACE = "Main";

    private static final String NAMESPACE_SEPARATOR = ":";

    /**
     * Convert a DokuWiki reference to a XWiki document reference.
     * <p>
     * References are converted as follows:
     * <ul>
     *     <li>namespace start pages ("start") are converted to the respective XWiki space (= non-terminal page)</li>
     *     <li>regular pages are converted to the corresponding non-terminal page in XWiki</li>
     *     <li>the DokuWiki start page is converted to the XWiki start page (Main)</li>
     * </ul>
     * @param dokuwikiReference the DokuWiki reference to convert
     * @return the converted XWiki document reference
     */
    public LocalDocumentReference getDocumentReference(String dokuwikiReference)
    {
        if (StringUtils.isBlank(dokuwikiReference) || dokuwikiReference.equals(DOKUWIKI_START_PAGE)) {
            return new LocalDocumentReference(Collections.singletonList(XWIKI_MAIN_SPACE), XWIKI_START_PAGE);
        }

        List<String> parts = Arrays.asList(dokuwikiReference.split(NAMESPACE_SEPARATOR));
        String pageName = parts.get(parts.size() - 1);
        LocalDocumentReference documentReference;
        if (pageName.equals(DOKUWIKI_START_PAGE)) {
            documentReference = new LocalDocumentReference(parts.subList(0, parts.size() - 1), XWIKI_START_PAGE);
        } else {
            documentReference = new LocalDocumentReference(parts, XWIKI_START_PAGE);
        }
        return documentReference;
    }

    /**
     * Convert a file path to a DokuWiki page to a DokuWiki reference.
     *
     * @param path the path to the file that contains the page
     * @param pagesDirectory the directory that contains all pages, must be an ancestor of the path
     * @return the DokuWiki reference
     */
    public String getDokuWikiReference(Path path, Path pagesDirectory)
    {
        // Extract all directories from the path excluding pagesDirectory.
        List<String> parentPath = new ArrayList<>();
        Path parent = path.getParent();
        while (!parent.equals(pagesDirectory)) {
            parentPath.add(parent.getFileName().toString());
            parent = parent.getParent();
        }

        Collections.reverse(parentPath);

        String fileNameWithoutExtension = StringUtils.removeEnd(path.getFileName().toString(), ".txt");

        if (parentPath.isEmpty()) {
            return fileNameWithoutExtension;
        } else {
            return String.join(NAMESPACE_SEPARATOR, parentPath) + NAMESPACE_SEPARATOR + fileNameWithoutExtension;
        }
    }
}
