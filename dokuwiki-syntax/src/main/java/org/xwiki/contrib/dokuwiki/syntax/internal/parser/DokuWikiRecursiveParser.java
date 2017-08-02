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


import org.xwiki.rendering.listener.*;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ParseException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.abs;
import static java.lang.Math.sin;

class DokuWikiRecursiveParser {
    //private static String[] supportedTags = new String[]{"<del>", "</del>", "<sub>", "</sub>", "<sup>", "</sup>", "<nowiki>", "</nowiki>"};
    private static Character[] specialSymbols = new Character[]{
            '@', '#', '$', '*', '%', '\'', '(', '!', ')', '-', '_', '^', '`', '?', ',', ';', '.', '/', ':', '=', '+', '<', '|', '>'};

    void parse(Reader source, Listener listener, MetaData metaData) throws ParseException {
        try {
            listener.beginDocument(metaData);
            parseRecursive(source, listener);
            listener.endDocument(metaData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseRecursive(Reader source, Listener listener) throws IOException {
        ArrayList<Character> buffer = new ArrayList<>();
        boolean inParagraph = false;
        boolean addNewParagraph = false;
        boolean onNewLineCharacter = false;
        boolean horizontalLineAdded = false;
        int readCharacter;
        boolean boldOpen = false;
        boolean italicOpen = false;
        boolean underlineOpen = false;
        boolean monospaceOpen = false;
        boolean linkOpen = true;
        int listSpaceidentation = -1;
        int quotationIdentation = -1;
        boolean inQuotation = false;
        boolean listEnded = true;
        boolean inSectionEvent = false;
        int headerLevel = 1;

        while (source.ready() && (readCharacter = source.read()) != -1) {
            buffer.add((char) readCharacter);
            if (getStringRepresentation(buffer).endsWith("----")) {
                //generate newline event
                listener.onHorizontalLine(Listener.EMPTY_PARAMETERS);
                //start paragraph
                listener.beginParagraph(Listener.EMPTY_PARAMETERS);
                inParagraph = true;
                addNewParagraph = false;
                horizontalLineAdded = true;
                buffer.clear();
                continue;
            }
            //remove unnecessary new line characters.
            if (buffer.size() > 0 && buffer.get(buffer.size() - 1) == '\n' && (addNewParagraph || horizontalLineAdded)) {
                buffer.subList(buffer.size() - 1, buffer.size()).clear();
                continue;
            }


            if (!(buffer.size() > 0 && (buffer.get(buffer.size() - 1) == '-' || buffer.get(buffer.size() - 1) == '>' || (buffer.contains('<') && !buffer.contains('@')) ||
                    buffer.get(buffer.size() - 1) == ' ' || buffer.get(buffer.size() - 1) == '*' || buffer.get(buffer.size() - 1) == '='
                    || buffer.get(buffer.size() - 1) == '<' || buffer.get(buffer.size() - 1) == '^' || !listEnded || inQuotation || inSectionEvent))) {
                if (!inParagraph) {
                    if (listSpaceidentation > -1 || quotationIdentation > -1) {
                        while (listSpaceidentation >= 0) {
                            listener.endListItem();
                            listener.endList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
                            listSpaceidentation--;
                        }
                        while (quotationIdentation >= 0) {
                            listener.endQuotationLine();
                            listener.endQuotation(Listener.EMPTY_PARAMETERS);
                            quotationIdentation--;
                        }
                    } else {
                        listener.beginParagraph(Listener.EMPTY_PARAMETERS);
                        inParagraph = true;
                    }
                    addNewParagraph = false;
                } else if (addNewParagraph) {
                    listener.endParagraph(Listener.EMPTY_PARAMETERS);
                    listener.beginParagraph(Listener.EMPTY_PARAMETERS);
                    addNewParagraph = false;
                }
            } else {
                if (addNewParagraph && inParagraph) {
                    listener.endParagraph(Listener.EMPTY_PARAMETERS);
                    inParagraph = false;
                    addNewParagraph = false;
                }
            }
            horizontalLineAdded = false;
            if (buffer.size() == 1 && inSectionEvent && buffer.get(0) == '=') {
                headerLevel++;
                buffer.clear();
            }
            if (buffer.size() >= 2) {
                if (getStringRepresentation(buffer).endsWith( "==")){
                    if (!inSectionEvent) {
                        inSectionEvent = true;
                        buffer.clear();
                    } else if (inSectionEvent) {
                        listener.beginSection(Listener.EMPTY_PARAMETERS);
                        //Dokuwiki doesn't use ids in headers
                        listener.beginHeader(HeaderLevel.parseInt(abs(7 - headerLevel)), "", Listener.EMPTY_PARAMETERS);
                        processWords(2, buffer, listener);
                        listener.endHeader(HeaderLevel.parseInt(headerLevel), "", Listener.EMPTY_PARAMETERS);
                        listener.endSection(Listener.EMPTY_PARAMETERS);
                        while (source.ready() && headerLevel > 1) {
                            source.read();
                            headerLevel--;
                        }
                        source.read();
                        inSectionEvent = false;
                    }
                    continue;
                }
                if (getStringRepresentation(buffer).equals("  ") && listSpaceidentation == -1) {
                    //code section
                    buffer.clear();
                    int c;
                    boolean endOfLine = false;
                    while (source.ready() && (c = source.read()) != -1) {
                        buffer.add((char) c);
                        if (((char) c ) == '\n') {
                            listener.onMacro("code", Listener.EMPTY_PARAMETERS,getStringRepresentation(buffer), false);
                            buffer.clear();
                            endOfLine = true;
                        }
                    }
                    if (!endOfLine) {
                        listener.onMacro("code", Listener.EMPTY_PARAMETERS,getStringRepresentation(buffer), false);
                        buffer.clear();
                    }
                    continue;
                }
                if (buffer.get(buffer.size() - 1) == '*' && buffer.get(buffer.size() - 2) == '*') {
                    //bold formatting parser
                    processWords(2, buffer, listener);
                    if (!boldOpen) {
                        listener.beginFormat(Format.BOLD, Listener.EMPTY_PARAMETERS);
                        boldOpen = true;

                    } else {
                        listener.endFormat(Format.BOLD, Listener.EMPTY_PARAMETERS);
                        boldOpen = false;
                    }
                    continue;
                }
                if (buffer.get(buffer.size() - 1) == ' ' && buffer.get(buffer.size() - 2) == '>') {
                    //process quotation.
                    if (buffer.size() - 2 > quotationIdentation) {
                        while (quotationIdentation < buffer.size() - 2) {
                            listener.beginQuotation(Listener.EMPTY_PARAMETERS);
                            listener.beginQuotationLine();
                            quotationIdentation++;
                        }
                    } else if (buffer.size() - 2 < quotationIdentation) {
                        while (quotationIdentation > (buffer.size() - 2)) {
                            listener.endQuotationLine();
                            listener.endQuotation(Listener.EMPTY_PARAMETERS);
                            quotationIdentation--;
                        }
                        listener.endQuotationLine();
                        listener.beginQuotationLine();
                    } else {
                        listener.endQuotationLine();
                        listener.beginQuotationLine();
                    }
                    inQuotation = true;
                    buffer.clear();
                    continue;
                }
                if (buffer.get(buffer.size() - 1) == ' ' && buffer.get(buffer.size() - 2) == '*') {
                    buffer.subList(buffer.size() - 2, buffer.size()).clear();
                    boolean isUnorederedList = true;
                    for (Character c : buffer) {
                        if (c != ' ') {
                            isUnorederedList = false;
                        }
                    }
                    //unordered list
                    if (isUnorederedList) {
                        if (buffer.size() > listSpaceidentation) {
                            while (listSpaceidentation < buffer.size()) {
                                listener.beginList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
                                listener.beginListItem();
                                listSpaceidentation++;
                            }
                        } else if (buffer.size() < listSpaceidentation) {
                            while (listSpaceidentation > (buffer.size())) {
                                listener.endListItem();
                                listener.endList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
                                listSpaceidentation--;
                            }
                            listener.endListItem();
                            listener.beginListItem();
                        } else {
                            listener.endListItem();
                            listener.beginListItem();
                        }

                        listEnded = false;
                        buffer.clear();
                        continue;
                    } else {
                        buffer.add('*');
                        buffer.add(' ');
                    }
                }

                if (buffer.get(buffer.size() - 1) == ' ' && buffer.get(buffer.size() - 2) == '-') {
                    //Ordered list
                    buffer.subList(buffer.size() - 2, buffer.size()).clear();
                    boolean isOrederedList = true;
                    for (Character c : buffer) {
                        if (c != ' ') {
                            isOrederedList = false;
                        }
                    }
                    if (isOrederedList) {
                        if (buffer.size() > listSpaceidentation) {
                            while (listSpaceidentation < buffer.size()) {
                                listener.beginList(ListType.NUMBERED, Listener.EMPTY_PARAMETERS);
                                listener.beginListItem();
                                listSpaceidentation++;
                            }
                        } else if (buffer.size() < listSpaceidentation) {
                            while (listSpaceidentation > (buffer.size())) {
                                listener.endListItem();
                                listener.endList(ListType.NUMBERED, Listener.EMPTY_PARAMETERS);
                                listener.endListItem();
                                listSpaceidentation--;
                            }
                            listener.beginListItem();
                        } else {
                            listener.endListItem();
                            listener.beginListItem();
                        }
                        listEnded = false;
                        buffer.clear();
                        continue;
                    } else {
                        buffer.add('-');
                        buffer.add(' ');
                    }
                }

                if (buffer.get(buffer.size() - 1) == '/' && buffer.get(buffer.size() - 2) == '/') {
                    //Italics format parser
                    if (!italicOpen && buffer.get(buffer.size() - 3) == ' ') {
                        processWords(2, buffer, listener);
                        listener.beginFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = true;
                        continue;
                    }
                    if (italicOpen && buffer.get(buffer.size() - 3) != ' ') {
                        //generate italic close event
                        processWords(2, buffer, listener);
                        listener.endFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = false;
                        continue;
                    }
                }

                if (buffer.get(buffer.size() - 1) == '_' && buffer.get(buffer.size() - 2) == '_') {
                    //underline format parser
                    if (!underlineOpen && buffer.get(buffer.size() - 3) == ' ') {
                        processWords(2, buffer, listener);
                        listener.beginFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = true;
                        continue;
                    }
                    if (buffer.get(buffer.size() - 3) != ' ') {
                        processWords(2, buffer, listener);
                        listener.endFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = false;
                        continue;
                    }
                }

                if (buffer.get(buffer.size() - 1) == '\'' && buffer.get(buffer.size() - 2) == '\'') {
                    //monospace format parser
                    if (!monospaceOpen && buffer.get(buffer.size() - 3) == ' ') {
                        processWords(2, buffer, listener);
                        listener.beginFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = true;
                        continue;
                    }
                    if (buffer.get(buffer.size() - 3) != ' ') {
                        processWords(2, buffer, listener);
                        listener.endFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = false;
                        continue;
                    }
                }
            }

            if (buffer.size() > 0 && buffer.get(buffer.size() - 1) == '\n') {
                //handle lists
                if (!listEnded || inQuotation) {
                    onNewLineCharacter = false;
                }
                if (buffer.size() >= 0) {
                    processWords(1, buffer, listener);
                }
                if (onNewLineCharacter) {
                    addNewParagraph = true;
                    onNewLineCharacter = false;
                    continue;
                } else if (listEnded && !inQuotation) {
                    onNewLineCharacter = true;
                }
                listEnded = true;
                inQuotation = false;
                buffer.clear();
            }

            if (getStringRepresentation(buffer).endsWith("[[")) {
                //handle link
                processWords(2, buffer, listener);
                processLink(source, listener);
                buffer.clear();
                continue;
            }

            if (getStringRepresentation(buffer).endsWith("\\ ")) {
                //generate newline event
                processWords(3, buffer, listener);
                listener.onNewLine();
                continue;
            }

            if (getStringRepresentation(buffer).endsWith("{{")) {

                processWords(2, buffer, listener);
                //handle media input
                processImage(source, listener);
                buffer.clear();
                continue;
            }

            if (getStringRepresentation(buffer).endsWith("^ ")) {
                //handle table
                boolean isTable = true;
                boolean hasVerticalTableHeaders = false;
                for (char c : buffer.subList(0, buffer.size() - 2)) {
                    if (c != ' ') {
                        isTable = false;
                    } else {
                        hasVerticalTableHeaders = true;
                    }
                }
                if (isTable) {
                    listener.beginTable(Listener.EMPTY_PARAMETERS);
                    buffer.clear();
                    listener.beginTableRow(Listener.EMPTY_PARAMETERS);
                    boolean inRow = true;
                    if (!hasVerticalTableHeaders) {
                        int cInt;
                        while (source.ready() && (cInt = source.read()) != -1) {
                            char c = (char) cInt;
                            if (c == '^') {
                                listener.beginTableHeadCell(Listener.EMPTY_PARAMETERS);
                                trimBuffer(buffer);
                                processWords(0, buffer, listener);
                                listener.endTableHeadCell(Listener.EMPTY_PARAMETERS);

                            } else if (c == '|') {
                                listener.beginTableCell(Listener.EMPTY_PARAMETERS);
                                trimBuffer(buffer);
                                processWords(0, buffer, listener);
                                listener.endTableCell(Listener.EMPTY_PARAMETERS);
                            } else if (c == '\n') {
                                buffer.clear();
                                listener.endTableRow(Listener.EMPTY_PARAMETERS);
                                inRow = false;
                                c = (char) source.read();
                                if (c == '|') {
                                    listener.beginTableRow(Listener.EMPTY_PARAMETERS);
                                    inRow = true;
                                } else {
                                    buffer.add(c);
                                    break;
                                }
                            } else {
                                buffer.add(c);
                            }
                        }
                    } else {
                        //handle vertical table
                    }
                    if (inRow) {
                        listener.endTableRow(Listener.EMPTY_PARAMETERS);
                    }
                    listener.endTable(Listener.EMPTY_PARAMETERS);
                }
            }
            if (buffer.size() > 0 && buffer.get(buffer.size() - 1) == '<') {
                //override syntax - supports inline
                if (inParagraph) {
                    processWords(1, buffer, listener);
                    buffer.add('<');
                }
                boolean inNoWikiTag = true;
                char[] noWikiTag = new char[]{'n', 'o', 'w', 'i', 'k', 'i', '>'};
                for (int i = 0; source.ready() && i < 7; i++) {
                    buffer.add((char) source.read());
                    if (noWikiTag[i] != buffer.get(buffer.size() - 1)) {
                        inNoWikiTag = false;
                        break;
                    }
                }
                if (inNoWikiTag) {
                    buffer.clear();
                    while (source.ready()) {
                        buffer.add((char) source.read());
                        if (buffer.get(buffer.size() - 1) == '>') {
                            buffer.subList(buffer.size() - 9, buffer.size()).clear();
                            listener.onVerbatim(getStringRepresentation(buffer), inParagraph, Listener.EMPTY_PARAMETERS);
                            buffer.clear();
                            break;
                        }
                    }
                }
            }

            if (getStringRepresentation(buffer).endsWith("%%")) {
                //Also override syntax (same as <nowiki>) but is always inline
                processWords(2, buffer, listener);
                while (source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size() - 1) == '%' && buffer.get(buffer.size() - 2) == '%') {
                        buffer.subList(buffer.size() - 2, buffer.size()).clear();
                        listener.onVerbatim(getStringRepresentation(buffer), true, Listener.EMPTY_PARAMETERS);
                        buffer.clear();
                        break;
                    }
                }
            }

            if (getStringRepresentation(buffer).startsWith("<") && getStringRepresentation(buffer).endsWith(">")
                    && getStringRepresentation(buffer).contains("@")) {
                //email address
                processEmailAddressFromBuffer(buffer, listener);
                break;
            }

            if (getStringRepresentation(buffer).startsWith("<code ") ||getStringRepresentation(buffer).startsWith("<code>") ||
                    getStringRepresentation(buffer).startsWith("<file ") ||getStringRepresentation(buffer).startsWith("<file>")) {
                //handle code block
                String language;
                int c;
                boolean readLangauge = false;
                HashMap<String, String> param = new HashMap<>();
                buffer.subList(0,5).clear();
                if (buffer.get(0) != '>') {
                    readLangauge = true;
                    buffer.clear();
                }
                //read language and code
                while (source.ready() && (c = source.read()) != -1) {
                    buffer.add((char) c);
                    if (readLangauge && ((char) c) == '>') {
                        if (buffer.contains(' ')) {
                            language = getStringRepresentation(buffer).substring(0, buffer.indexOf(' ')).trim();
                        } else {
                            language = getStringRepresentation(buffer).substring(0, buffer.size() - 1).trim();
                        }
                        param.put("language", language);
                        buffer.clear();
                        readLangauge = false;
                        //consume a newLine character.
                        source.read();
                    }

                    if (getStringRepresentation(buffer).endsWith("</code>") || getStringRepresentation(buffer).endsWith("</file>")) {
                        buffer.subList(buffer.size() - 8, buffer.size()).clear();
                        listener.onMacro("code", param,getStringRepresentation(buffer), false);
                        buffer.clear();
                        //consume a newLine character.
                        source.read();
                    }
                }
                continue;
            }


            if (getStringRepresentation(buffer).endsWith("<sub>")) {
                //generate subscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.SUBSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("</sub>")) {
                //generate subscript close event
                processWords(6, buffer, listener);
                listener.endFormat(Format.SUBSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("<sup>")) {
                //generate superscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.SUPERSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("</sup>")) {
                //generate superscript close event
                processWords(6, buffer, listener);
                listener.endFormat(Format.SUPERSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("<del>")) {
                //generate strikeout open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.STRIKEDOUT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("</del>")) {
                //generate strikeout open event
                processWords(6, buffer, listener);
                listener.endFormat(Format.STRIKEDOUT, Listener.EMPTY_PARAMETERS);
            }
        }
        //parse remaining as strings
        if (buffer.size() > 0) {
            processWords(0, buffer, listener);
        }
        //close remaining list items
        while (listSpaceidentation >= 0) {
            listener.endListItem();
            listener.endList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
            listSpaceidentation--;
        }
        //close remaining quotations
        while (quotationIdentation >= 0) {
            listener.endQuotationLine();
            listener.endQuotation(Listener.EMPTY_PARAMETERS);
            quotationIdentation--;
        }
        if (inParagraph) {
            listener.endParagraph(Listener.EMPTY_PARAMETERS);
            inParagraph = false;
        }
    }

    private void processWords(int argumentTrimSize, ArrayList<Character> buffer, Listener listener) {
        buffer.subList(buffer.size() - argumentTrimSize, buffer.size()).clear();
        StringBuilder word = new StringBuilder();
        for (char c : buffer) {
            if (c == ' ' && word.length() == 0) {
                listener.onSpace();
            } else if (c == ' ') {
                processWord(word, listener);
                listener.onSpace();
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) {
            processWord(word, listener);
        }
        buffer.clear();
    }

    private void processWord(StringBuilder word, Listener listener) {
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
    }

    private void processWordsFromReader(Reader source, Listener listener, String endString) throws IOException {
        ArrayList<Character> buffer = new ArrayList<>();
        int c;
        while (source.ready() && (c = source.read()) != -1) {
            buffer.add((char) c);
            String readString = getStringRepresentation(buffer);
            if (readString.endsWith(endString)) {
                buffer.subList(buffer.size() - endString.length(), buffer.size()).clear();
                //StringBuilder here has no utility.
                processWords(0, buffer, listener);
                break;
            }
        }
    }

    private void processImage(Reader source, Listener listener) throws IOException {
        StringBuilder imageNameBuilder = new StringBuilder();
        int c;
        while (source.ready() && (c = source.read()) != -1) {
            imageNameBuilder.append((char) c);
            String imageArgument = imageNameBuilder.toString();
            boolean internalImage = true;
            HashMap<String, String> param = new HashMap<>();
            if (imageArgument.endsWith("}}")) {
                String imageName;
                if (!imageArgument.contains("wiki:")) {
                    internalImage = false;
                }
                if (imageArgument.startsWith("{{ ") && imageArgument.endsWith(" }}")) {
                    //align centre
                    param.put("align", "middle");
                } else if (imageArgument.startsWith(" ")) {
                    //align left
                    param.put("align", "left");
                } else if (imageArgument.endsWith(" }}")) {
                    //align right
                    param.put("align", "right");
                }
                if (internalImage) {
                    imageName = imageArgument.substring(5, imageArgument.length() - 2);

                } else {
                    imageName = imageArgument.substring(1, imageArgument.length() - 2);
                }
                imageArgument = imageName.trim();
                if (imageName.contains("|")) {
                    //there's a caption
                    String caption = imageName.substring(imageName.indexOf('|') + 1);
                    imageName = imageName.substring(0, imageName.indexOf('|'));
                    param.put("alt", caption);
                    param.put("title", caption);
                }
                if (imageName.contains("?")) {
                    //there's size information
                    String size = imageName.substring(imageName.indexOf('?') + 1);
                    imageName = imageName.substring(0, imageName.indexOf('?'));
                    if (size.contains("x")) {
                        param.put("height", size.substring(0, size.indexOf("x")) + "px");
                        param.put("width", size.substring(size.indexOf("x") +1) + "px");
                    } else {
                        param.put("width",size + "px");
                    }
                }
                ResourceReference reference = new ResourceReference(imageName, ResourceType.URL);
                reference.setTyped(false);
                listener.onImage(reference, false, param);
                break;
            }
        }
    }

    private void processLink(Reader source, Listener listener) throws IOException {
        ArrayList<Character> functionBuffer = new ArrayList<>();
        int c;
        while (source.ready() && (c = source.read()) != -1) {
            //String linkArgument = getStringRepresentation(functionBuffer);
            // boolean internalLink = true;
            functionBuffer.add((char) c);
            if ((char)c == '|') {
                ResourceReference reference;
                if (functionBuffer.get(0) == '<' && functionBuffer.get(functionBuffer.size() -2) == '>'
                        && functionBuffer.contains('@')) {
                    //process mailto
                    reference = new ResourceReference(getStringRepresentation(
                            new ArrayList<>(functionBuffer.subList(1, functionBuffer.size() -2))), ResourceType.MAILTO);
                    reference.setTyped(true);
                } else {
                    reference = new ResourceReference(getStringRepresentation(
                            new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() -1))), ResourceType.URL);
                    reference.setTyped(false);
                }
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                processWordsFromReader(source, listener, "]]");
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }
            if (getStringRepresentation(functionBuffer).endsWith("]]")) {
                ResourceReference reference = new ResourceReference(getStringRepresentation(
                        new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() -2))), ResourceType.URL);
                reference.setTyped(false);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }

        }
    }
    private void processEmailAddressFromBuffer(ArrayList<Character> buffer, Listener listener) {
        buffer.remove(0);
        buffer.remove(buffer.size() - 1);
        ResourceReference reference = new ResourceReference(getStringRepresentation(buffer), ResourceType.MAILTO);
        listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
        listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
        buffer.clear();
    }

    //generic helper methods
    private String getStringRepresentation(ArrayList<Character> list) {
        StringBuilder builder = new StringBuilder(list.size());
        for (Character ch : list) {
            builder.append(ch);
        }
        return builder.toString();
    }

    private boolean checkURL(String string) {
        String URL_REGEX = "^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$";
        Pattern p = Pattern.compile(URL_REGEX);
        Matcher m = p.matcher(string);
        if (m.find()) {
            return true;
        } else {
            return false;
        }
    }

    private void trimBuffer(ArrayList<Character> buffer) {
        if (buffer.get(0) == ' ') {
            buffer.remove(0);
        }
        if (buffer.get(buffer.size() - 1) == ' ') {
            buffer.remove(buffer.size() - 1);
        }
    }
}