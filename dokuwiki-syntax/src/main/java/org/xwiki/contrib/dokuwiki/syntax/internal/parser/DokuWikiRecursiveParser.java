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


import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.parser.ParseException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

class DokuWikiRecursiveParser {
    private static boolean boldOpen = false;
    private static boolean italicOpen = false;
    private static boolean underlineOpen = false;
    private static boolean monospaceOpen = false;





    void parse(Reader source, Listener listener) throws ParseException {
        try {
            parseRecursive(source, listener);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseRecursive(Reader source, Listener listener) throws IOException {
        ArrayList<Character> buffer = new ArrayList<>();
        StringBuilder bufferString = new StringBuilder();
        boolean newLineCharacterAdded = false;

        while (source.ready()) {
            buffer.add((char) source.read());
            //remove unnecessary new line characters.
            if (buffer.get(buffer.size()) == '\n' && newLineCharacterAdded) {
                buffer.subList(buffer.size() - 1, buffer.size()).clear();
                continue;
            }
            newLineCharacterAdded = false;
            bufferString.append(getStringRepresentation(buffer));
            if (buffer.get(buffer.size()) == '*' && buffer.get(buffer.size() - 1) == '*') {
                if (!boldOpen) {
                    //generate bold open event
                    boldOpen = true;
                } else {
                    //generate bold close event
                    boldOpen = false;
                }
                buffer.subList(buffer.size() - 2, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 2, bufferString.length());
                parseRecursive(source, listener);
            }
            if (buffer.get(buffer.size()) == '/' && buffer.get(buffer.size() - 1) == '/') {
                if (!italicOpen) {
                    //generate italic event open on chars
                    italicOpen = true;
                } else {
                    //generate italic close event
                    italicOpen = false;
                }
                buffer.subList(buffer.size() - 2, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 2, bufferString.length());
                parseRecursive(source, listener);
            }
            if (buffer.get(buffer.size()) == '_' && buffer.get(buffer.size() - 1) == '_') {
                if (!underlineOpen) {
                    //generate underline event open on chars
                    underlineOpen = true;
                } else {
                    //generate underline close event
                    underlineOpen = false;
                }
                buffer.subList(buffer.size() - 2, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 2, bufferString.length());
                parseRecursive(source, listener);
            }
            if (buffer.get(buffer.size()) == '\'' && buffer.get(buffer.size() - 1) == '\'') {
                if (!monospaceOpen) {
                    //generate monospace event open on chars
                    monospaceOpen = true;
                } else {
                    //generate monospace close event
                    monospaceOpen = false;
                }
                buffer.subList(buffer.size() - 2, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 2, bufferString.length());
                parseRecursive(source, listener);
            }
            if (buffer.get(buffer.size()) == '\n') {
                //generate newline event
                newLineCharacterAdded =true;
                buffer.subList(buffer.size() - 1, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 1, bufferString.length());
            }

            if (bufferString.toString().equals("\\\\\n") || bufferString.toString().equals("\\\\ ")) {
                //generate newline event
                buffer.subList(buffer.size() - 3, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 3, bufferString.length());
            }
            if (bufferString.toString().equals("<sub>")) {
                //generate subscript event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 5, bufferString.length());
                parseRecursive(source, listener);
            }
            if (bufferString.toString().equals("<sub>")) {
                //generate subscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 5, bufferString.length());
                parseRecursive(source, listener);
            }
            if (bufferString.toString().equals("</sub>")) {
                //generate subscript close event
                buffer.subList(buffer.size() - 6, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 6, bufferString.length());
                parseRecursive(source, listener);
            }
            if (bufferString.toString().equals("<del>")) {
                //generate strikeout open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 5, bufferString.length());
                parseRecursive(source, listener);
            }
            if (bufferString.toString().equals("</sub>")) {
                //generate strikeout close event
                buffer.subList(buffer.size() - 6, buffer.size()).clear();
                bufferString.delete(bufferString.length() - 6, bufferString.length());
                parseRecursive(source, listener);
            }
        }
        //parse remaining as strings

    }

    //helper methods
    private String getStringRepresentation(ArrayList<Character> list) {
        StringBuilder builder = new StringBuilder(list.size());
        for (Character ch : list) {
            builder.append(ch);
        }
        return builder.toString();
    }

}
