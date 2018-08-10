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
package org.xwiki.contrib.dokuwiki.syntax;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * DokuWiki parser helper method component.
 *
 * @version $Id: $
 * @since 1.3
 */
@Component(roles = DokuWikiSyntaxParserHelper.class)
@Singleton
public class DokuWikiSyntaxParserHelper
{
    /**
     * Builds a string from the buffer list.
     *
     * @param list List is input buffer ArrayList.
     * @return builder Builder to build string of input list.
     */
    public String getStringRepresentation(ArrayList<Character> list)
    {
        StringBuilder builder = new StringBuilder(list.size());
        for (Character ch : list) {
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * Process words based on space split.
     *
     * @param buffer Buffer stores list of characters.
     * @param paragraphJustOpened Keeps track of paragraph tag.
     * @param argumentTrimSize : Number of characters removed from input buffer.
     * @param listener Listener calls the java events.
     * @return paragraphOpenedLocal Returns the current status of paragraph tag.
     */
    public boolean processWords(int argumentTrimSize, ArrayList<Character> buffer, Listener listener,
            boolean paragraphJustOpened)
    {
        buffer.subList(buffer.size() - argumentTrimSize, buffer.size()).clear();
        StringBuilder word = new StringBuilder();
        boolean spaceAdded = false;
        boolean paragraphOpenedLocal = paragraphJustOpened;
        for (char c : buffer) {
            if (c == ' ') {
                if (!spaceAdded) {
                    if (word.length() > 0) {
                        paragraphOpenedLocal = processWord(word, listener, paragraphOpenedLocal);
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
            paragraphOpenedLocal = processWord(word, listener, paragraphOpenedLocal);
        }
        buffer.clear();
        return paragraphOpenedLocal;
    }

    private boolean processWord(StringBuilder word, Listener listener, boolean paragraphJustOpened)
    {
        Character[] specialSymbols =
                    new Character[]{ '@', '#', '$', '*', '%', '\'', '(', '!', ')', '-', '_', '^', '`', '?', ',', ';',
                        '.', '/', ':', '=', '+', '<', '|', '>' };
        boolean paragraphOpenedLocal = paragraphJustOpened;
        if (paragraphOpenedLocal) {
            paragraphOpenedLocal = false;
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
        return paragraphOpenedLocal;
    }

    /**
     * Read characters uptil } into buffer.
     *
     * @param functionBuffer functionBuffer stores list of characters
     * @param source Source is the input stream
     * @return buffer
     * @throws IOException when fail to parse input buffer
     */
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
