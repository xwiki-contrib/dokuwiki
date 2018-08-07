package org.xwiki.contrib.dokuwiki.syntax.plugins.internal.angleBrackets;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import org.xwiki.component.annotation.Role;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki Angle Bracket Plugin parser.
 *
 * @version $Id:  $
 * @since 1.2
 */
@FunctionalInterface
@Role
public interface DokuWikiAngleBracketPlugin
{
    void parse(Listener listener, Reader source, ArrayList<Character> buffer) throws IOException;
}
