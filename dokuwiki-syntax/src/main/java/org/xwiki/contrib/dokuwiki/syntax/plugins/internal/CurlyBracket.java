package org.xwiki.contrib.dokuwiki.syntax.plugins.internal;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiIterativeParser;
import org.xwiki.contrib.dokuwiki.syntax.plugins.DokuWikiPlugin;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki curly plugin parser.
 *
 * @version $Id: $
 */
@Component
@Named("curlyBracket")
@Singleton
public class CurlyBracket implements DokuWikiPlugin
{
    @Inject
    @Named("rss") private
    PluginParser pluginParser;

    private DokuWikiIterativeParser helper = new DokuWikiIterativeParser();

    @Override public void parse(ArrayList<Character> buffer, Reader source, Listener listener) throws IOException
    {
        if (helper.getStringRepresentation(buffer).contains("{{")) {
            helper.processWords(2, buffer, listener);
            buffer.add('{');
            buffer.add('{');
            helper.readIntoBuffer(buffer, source);
            //buffer

            pluginParser.parse(listener, source, buffer);

            if (helper.getStringRepresentation(buffer).contains("gallery>")) {
                // handle gallery Plugin parser
            }

            if (helper.getStringRepresentation(buffer).contains("youtube>") || helper.getStringRepresentation(buffer)
                    .contains("vimeo>")
                    || helper.getStringRepresentation(buffer).contains("dailymotion>"))
            {
                //  handle video Plugin parser
            }
        }
    }
}
