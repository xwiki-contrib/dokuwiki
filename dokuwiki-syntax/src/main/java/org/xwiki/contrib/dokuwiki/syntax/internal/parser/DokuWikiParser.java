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

import java.io.Reader;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.internal.parser.XDOMGeneratorListener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.syntax.Syntax;

/**
 * DokuWiki block parser.
 *
 * @version $Id: 82fb920b4209cb0616bebf0e226334e618a28144 $
 */
@Component
@Named(DokuWikiStreamParser.SYNTAX_STRING)
@Singleton
public class DokuWikiParser implements Parser
{
    /**
     * Streaming Markdown Parser.
     */
    @Inject
    @Named(DokuWikiStreamParser.SYNTAX_STRING)
    private StreamParser dokuwikiStreamParser;

    @Override
    public Syntax getSyntax()
    {
        return DokuWikiStreamParser.SYNTAX;
    }

    @Override
    public XDOM parse(Reader source) throws ParseException
    {
        XDOMGeneratorListener xdomGeneratorListener = new XDOMGeneratorListener();
        this.dokuwikiStreamParser.parse(source, xdomGeneratorListener);
        return xdomGeneratorListener.getXDOM();
    }
}
