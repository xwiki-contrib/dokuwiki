package org.xwiki.contrib.dokuwiki.syntax;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;


/**
 * DokuWiki parser helper method component.
 *
 * @version $Id: $
 * @since 1.2
 */
@Component
@Named("helper")
@Singleton
public class Helper
{
    public String getStringRepresentation(ArrayList<Character> list)
    {
        StringBuilder builder = new StringBuilder(list.size());
        for (Character ch : list) {
            builder.append(ch);
        }
        return builder.toString();
    }

    public boolean processWords(int argumentTrimSize, ArrayList<Character> buffer, Listener listener, boolean paragraphJustOpened)
    {
          buffer.subList(buffer.size() - argumentTrimSize, buffer.size()).clear();
        StringBuilder word = new StringBuilder();
        boolean spaceAdded = false;
        for (char c : buffer) {
            if (c == ' ') {
                if (!spaceAdded) {
                    if (word.length() > 0) {
                        processWord(word, listener, paragraphJustOpened);
                    }
                    listener.onSpace();
                    spaceAdded = true;
                }
            } else {
                word.append(c);
                spaceAdded = false;
            }
        }
        if (word.length() > 0) {
            processWord(word, listener, paragraphJustOpened);
        }
        buffer.clear();
        return paragraphJustOpened;
    }

    private boolean processWord(StringBuilder word, Listener listener, boolean paragraphJustOpened)
    {
        Character[] specialSymbols =
                new Character[]{ '@', '#', '$', '*', '%', '\'', '(', '!', ')', '-', '_', '^', '`', '?', ',', ';',
                        '.', '/', ':', '=', '+', '<', '|', '>' };

        if (paragraphJustOpened) {
            paragraphJustOpened = false;
        }
        if (Arrays.asList(specialSymbols).contains(word.charAt(0)) && word.length() == 1) {
            //check if special symbol
            listener.onSpecialSymbol(word.charAt(0));
        } else if (checkURL(word.toString())) {
            ResourceReference reference = new ResourceReference(word.toString(), ResourceType.URL);
            reference.setTyped(false);
            listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
            listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
        } else {
            listener.onWord(word.toString());
        }
        word.setLength(0);
        return paragraphJustOpened;
    }

    public ArrayList<Character> readIntoBuffer(ArrayList<Character> functionBuffer, Reader source) throws IOException
    {
        int c;
        while (source.ready()) {
            c = source.read();
            if (c == '}') {
                functionBuffer.add((char) c);
                functionBuffer.add('}');
                source.read();
                break;
            }
            functionBuffer.add((char) c);
        }
        return functionBuffer;
    }

    private boolean checkURL(String string)
    {
        String urlRegex = "^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$";
        Pattern p = Pattern.compile(urlRegex);
        Matcher m = p.matcher(string);
        return m.find();
    }
}
