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
package org.xwiki.contrib.dokuwiki.syntax.internal.parser;

import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.Listener;

/**
 * Parse inline plain text.
 *
 * @version $Id$
 */
@Component
@Named("inline/plain")
@Singleton
public class InlinePlainParser implements SingleDokuWikiSyntaxParser
{
    private static final Pattern SPECIALSYMBOL_PATTERN = Pattern.compile("[!\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~\\\\]");

    /**
     * Parse the given text as plain text and send events to the given listener.
     *
     * @param text the text to parse
     * @param listener the listener to send events to
     */
    @Override
    public void parse(String text, Listener listener)
    {
        // Iterate over the characters in the text. If there is a space or special character, send the last word,
        // otherwise add the character to the current word.
        StringBuilder word = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (Character.isWhitespace(c) || SPECIALSYMBOL_PATTERN.matcher(Character.toString(c)).matches()) {
                if (word.length() > 0) {
                    listener.onWord(word.toString());
                    word.setLength(0);
                }

                if (Character.isWhitespace(c)) {
                    listener.onSpace();
                } else {
                    listener.onSpecialSymbol(c);
                }
            } else {
                word.append(c);
            }
        }

        if (word.length() > 0) {
            listener.onWord(word.toString());
        }
    }
}
