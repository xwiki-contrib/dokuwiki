package org.xwiki.contrib.dokuwiki.syntax.plugins.internal.angleBrackets;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxParserHelper;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki Code plugin parser.
 *
 * @version $Id: $
 * @since 1.2
 */
@Component
@Named("code")
@Singleton
public class Code implements DokuWikiAngleBracketPlugin
{
    @Inject
    private DokuWikiSyntaxParserHelper helper;

    @Override public void parse(ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException, ComponentLookupException
    {
    }
}
