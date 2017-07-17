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

import java.io.IOException;
import java.io.Reader;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties.ReferenceType;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.input.BeanInputFilterStreamFactory;
import org.xwiki.filter.input.DefaultReaderInputSource;
import org.xwiki.filter.input.InputFilterStream;
import org.xwiki.filter.input.InputFilterStreamFactory;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxType;

import static org.xwiki.rendering.listener.Listener.EMPTY_PARAMETERS;

/**
 * DokuWiki streamed parser
 *
 * @version $Id: fcd59f6c7ae81ffec64f5df3ca333eca4eaf18b3 $
 */
@Component
@Named(org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser.SYNTAX_STRING)
@Singleton
public class DokuWikiStreamParser implements StreamParser
{
    /**
     * The syntax type.
     */
    public static final SyntaxType SYNTAX_TYPE = new SyntaxType("dokuwiki", "DokuWiki");

    /**
     * The syntax with version.
     */
    public static final Syntax SYNTAX = new Syntax(SYNTAX_TYPE, "1.0");

    /**
     * The String version of the syntax.
     */
    public static final String SYNTAX_STRING = "dokuwiki/1.0";

    @Inject
    @Named(org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties.FILTER_STREAM_TYPE_STRING)
    private InputFilterStreamFactory filter;

    @Override
    public Syntax getSyntax()
    {
        return SYNTAX;
    }

    @Override
    public void parse(Reader source, Listener listener) throws ParseException
    {
        try {
            source.read();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
