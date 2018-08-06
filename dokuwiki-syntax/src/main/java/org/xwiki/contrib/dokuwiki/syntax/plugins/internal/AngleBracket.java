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
 * DokuWiki angle plugin parser.
 *
 * @version $Id: $
 */
@Component
@Named("angleBracket")
@Singleton
public class AngleBracket implements DokuWikiPlugin
{
    private DokuWikiIterativeParser helper = new DokuWikiIterativeParser();


    @Inject
    @Named("code") private
    PluginParser pluginParser;


    @Override public void parse(ArrayList<Character> buffer, Reader source, Listener listener) throws IOException
    {
//        if (helper.getStringRepresentation(buffer).contains("<<")){
//            if (helper.getStringRepresentation(buffer).contains("<code ")
//                    || helper.getStringRepresentation(buffer).endsWith("<code>")
//                    || helper.getStringRepresentation(buffer).endsWith("<file ")
//                    || helper.getStringRepresentation(buffer).endsWith("<file>"))
//            {
//                //handle code block
//                String language;
//                int c;
//                boolean readLangauge = false;
//                Map<String, String> param = new HashMap<>();
//                boolean inCodeBlock = true;
//                if (buffer.get(5) != '>') {
//                    readLangauge = true;
//                }
//                helper.processWords(6, buffer, listener);
//
//                //read language and code
//                c = source.read();
//                while (source.ready() && c != -1) {
//                    buffer.add((char) c);
//                    if (readLangauge && ((char) c) == '>') {
//                        if (buffer.contains(' ')) {
//                            language = helper.getStringRepresentation(buffer).
// substring(0, buffer.indexOf(' ')).trim();
//                        } else {
//                            language = helper.getStringRepresentation(buffer).
// substring(0, buffer.size() - 1).trim();
//                        }
//                        param.put("language", language);
//                        buffer.clear();
//                        readLangauge = false;
//                        //consume a newLine character.
//                        source.read();
//                    }
//
//                    if (helper.getStringRepresentation(buffer).endsWith("</code>")
//                            || helper.getStringRepresentation(buffer).endsWith("</file>"))
//                    {
//                        if (buffer.contains('\n')) {
//                            buffer.subList(buffer.size() - 8, buffer.size()).clear();
//                        } else {
//                            buffer.subList(buffer.size() - 7, buffer.size()).clear();
//                        }
//                        if (inCodeBlock) {
//                            listener.onMacro("code", param, helper.getStringRepresentation(buffer), false);
//                            inCodeBlock = false;
//                        }
//                        buffer.clear();
//                        //consume a newLine character.
//                        source.read();
//                        break;
//                    }
//                    c = source.read();
//                }
//                continue;
//            }
////        }
        return;
    }
}
