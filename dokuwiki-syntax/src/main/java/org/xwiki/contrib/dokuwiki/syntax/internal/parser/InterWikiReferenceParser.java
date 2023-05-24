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

import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.reference.InterWikiResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;

/**
 * Parse DokuWiki interwiki reference syntax as resource reference.
 *
 * @version $Id$
 */
@Component(roles = InterWikiReferenceParser.class)
@Singleton
public class InterWikiReferenceParser
{
    private static final Pattern INTERWIKI_PATTERN = Pattern.compile("^[a-zA-Z0-9.]+>");

    /**
     * Parse a DokuWiki interwiki reference syntax as resource reference.
     *
     * @param reference the DokuWiki interwiki reference syntax to parse
     * @return the resource reference
     */
    public ResourceReference parse(String reference)
    {
        String[] interWikiParts = StringUtils.splitByWholeSeparatorPreserveAllTokens(reference, ">", 2);
        InterWikiResourceReference interWikiReference = new InterWikiResourceReference(interWikiParts[1]);
        interWikiReference.setInterWikiAlias(interWikiParts[0]);
        return interWikiReference;
    }

    /**
     * @param reference the reference to check
     * @return true if the given reference is an interwiki reference
     */
    public boolean isInterWikiReference(String reference)
    {
        return INTERWIKI_PATTERN.matcher(reference).find();
    }
}
