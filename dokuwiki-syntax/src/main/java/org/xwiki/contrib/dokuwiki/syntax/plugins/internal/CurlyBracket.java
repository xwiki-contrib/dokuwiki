package org.xwiki.contrib.dokuwiki.syntax.plugins.internal;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxParserHelper;
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
    private Logger logger;

    @Inject
    private DokuWikiSyntaxParserHelper helper;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    private List<DokuWikiCurlyBracketPlugin> componentList;

    @Override public void parse(ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException, ComponentLookupException
    {
        if (helper.getStringRepresentation(buffer).contains("{{")) {
            helper.processWords(2, buffer, listener, false);
            buffer.add('{');
            buffer.add('{');
            helper.readIntoBuffer(buffer, source);
        }

        List<DokuWikiCurlyBracketPlugin> componentList =
                componentManagerProvider.get().getInstanceList(DokuWikiCurlyBracketPlugin.class);
        for (DokuWikiCurlyBracketPlugin plugin : componentList) {
            plugin.parse(buffer, source, listener);
        }
    }
}
