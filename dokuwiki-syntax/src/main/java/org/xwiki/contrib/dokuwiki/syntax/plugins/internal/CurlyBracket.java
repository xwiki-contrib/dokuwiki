package org.xwiki.contrib.dokuwiki.syntax.plugins.internal;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.dokuwiki.syntax.Helper;
import org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiIterativeParser;
import org.xwiki.contrib.dokuwiki.syntax.plugins.DokuWikiPlugin;
import org.xwiki.contrib.dokuwiki.syntax.plugins.internal.curlyBrackets.DokuWikiCurlyBracketPlugin;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki curly plugin parser.
 *
 * @version $Id: $
 * @since 1.2
 */
@Component
@Named("curlyBracket")
@Singleton
public class CurlyBracket implements DokuWikiPlugin
{
    @Inject
    @Named("helper") private Helper helper;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;


    @Override public void parse(ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException, ComponentLookupException
    {

        List<DokuWikiPlugin> componentList = componentManagerProvider.get().getInstanceList(DokuWikiCurlyBracketPlugin.class);
        for (DokuWikiPlugin plugin : componentList) {
            plugin.parse(buffer, source, listener);
        }

        if (helper.getStringRepresentation(buffer).contains("{{")) {
            helper.processWords(2, buffer, listener, false );
            buffer.add('{');
            buffer.add('{');
            helper.readIntoBuffer(buffer, source);
        }
    }
}
