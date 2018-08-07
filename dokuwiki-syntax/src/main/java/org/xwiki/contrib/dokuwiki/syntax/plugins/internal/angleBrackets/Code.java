package org.xwiki.contrib.dokuwiki.syntax.plugins.internal.angleBrackets;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
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
class Code implements  DokuWikiAngleBracketPlugin
{
    @Override public void parse(Listener listener, Reader source, ArrayList<Character> buffer) throws IOException
    {

    }
}
