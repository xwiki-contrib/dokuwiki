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

import org.xwiki.component.annotation.Role;
import org.xwiki.rendering.listener.Listener;

/**
 * Parser for a single isolated syntax in a DokuWiki document like a link or an image.
 *
 * @version $Id$
 * @since 2.0
 */
@Role
public interface SingleDokuWikiSyntaxParser
{
    /**
     * Parse the given content and call the listener to generate the corresponding events.
     *
     * @param content the content to parse
     * @param listener the listener to call to generate events
     */
    void parse(String content, Listener listener);
}
