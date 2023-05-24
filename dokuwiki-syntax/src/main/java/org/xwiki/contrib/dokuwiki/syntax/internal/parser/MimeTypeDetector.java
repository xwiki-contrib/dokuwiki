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
package org.xwiki.contrib.dokuwiki.syntax.internal.parser;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;

/**
 * Detect the mimetype of a URL or filename.
 *
 * @version $Id$
 * @since 2.0
 */
@Component(roles = MimeTypeDetector.class)
@Singleton
public class MimeTypeDetector implements Initializable
{
    private static final String NAMESPACE_SEPARATOR = ":";

    private static final String PATH_SEPARATOR = "/";

    private Tika tika;

    @Override
    public void initialize()
    {
        this.tika = new Tika();
    }

    /**
     * @param name the filename or URL to detect the mime type of
     * @return the mime type of the filename or URL
     */
    public String detectMimeType(String name)
    {
        // Tika tries parsing the file name as URI and something like space:image.png won't have a path then.
        String fileName = name;
        if (fileName.contains(NAMESPACE_SEPARATOR)) {
            fileName = StringUtils.substringAfterLast(fileName, NAMESPACE_SEPARATOR);
        }

        if (fileName.contains(PATH_SEPARATOR)) {
            fileName = StringUtils.substringAfterLast(fileName, PATH_SEPARATOR);
        }

        return this.tika.detect(fileName);
    }
}
