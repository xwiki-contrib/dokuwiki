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
package org.xwiki.contrib.dokuwiki.syntax.plugins;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import org.xwiki.component.annotation.Role;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki Curly Bracket Plugin parser.
 *
 * @version $Id:  $
 * @since 1.3
 */
@FunctionalInterface
@Role
public interface DokuWikiCurlyBracketPlugin
{
    /**
     * Parse the input buffer.
     * @param buffer Buffer stores list of characters
     * @param source Source is the input stream
     * @param listener Listener calls the java events.
     * @throws IOException when fail to parse input buffer
     */
    void parse(ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException;
}
