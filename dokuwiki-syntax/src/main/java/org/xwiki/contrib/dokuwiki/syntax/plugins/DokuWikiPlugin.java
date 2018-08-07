package org.xwiki.contrib.dokuwiki.syntax.plugins;


import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import org.xwiki.component.annotation.Role;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki plugin parser.
 *
 * @version $Id: $
 * @since 1.2
 */
@Role
public interface DokuWikiPlugin
{
    void parse(ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException, ComponentLookupException;
}
