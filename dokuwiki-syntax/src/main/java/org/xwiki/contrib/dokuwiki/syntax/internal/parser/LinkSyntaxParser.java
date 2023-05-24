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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * Parse the content of a DokuWiki link syntax.
 *
 * @version $Id$
 */
@Component
@Named("link")
@Singleton
public class LinkSyntaxParser implements SingleDokuWikiSyntaxParser
{
    private static final String LABEL_SEPARATOR = "|";

    @Inject
    @Named("image")
    private SingleDokuWikiSyntaxParser imageSyntaxParser;

    @Inject
    private InterWikiReferenceParser interWikiReferenceParser;

    @Inject
    @Named("inline/plain")
    private SingleDokuWikiSyntaxParser inlinePlainParser;

    @Override
    public void parse(String link, Listener listener)
    {
        // Split the string at "|"
        String[] linkParts = StringUtils.splitByWholeSeparatorPreserveAllTokens(link, LABEL_SEPARATOR, 2);
        String linkTarget = linkParts[0].trim();

        ResourceReference reference;

        // Check if the link is an interwiki link
        if (this.interWikiReferenceParser.isInterWikiReference(linkTarget)) {
            reference = this.interWikiReferenceParser.parse(linkTarget);
        } else {
            // Store as untyped reference, let the converter deal with the special cases for DokuWiki import.
            reference = new ResourceReference(linkTarget, ResourceType.URL);
            reference.setTyped(false);
        }

        listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);

        if (linkParts.length == 2 && StringUtils.isNotBlank(linkParts[1])) {
            String linkLabel = linkParts[1];
            // Check if the link label is an image
            if (linkLabel.startsWith("{{") && linkLabel.endsWith("}}")) {
                this.imageSyntaxParser.parse(linkLabel.substring(2, linkLabel.length() - 2), listener);
            } else {
                this.inlinePlainParser.parse(linkLabel, listener);
            }
        }

        listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
    }
}
