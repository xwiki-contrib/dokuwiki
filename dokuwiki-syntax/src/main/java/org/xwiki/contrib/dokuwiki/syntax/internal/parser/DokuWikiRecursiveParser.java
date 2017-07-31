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

import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ParseException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;

class DokuWikiRecursiveParser {
    private static String[] supportedTags = new String[]{"<del>", "</del>", "<sub>", "</sub>", "<sup>", "</sup>", "<nowiki>", "</nowiki>"};
    private static Character[] specialSymbols = new Character[] {
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
        int quotationIdentation  = -1;
        boolean inQuotation = false;
        boolean listEnded = true;

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

            if (!(buffer.size() > 0 && (buffer.get(buffer.size() - 1) == '-' ||buffer.get(buffer.size() - 1) == '>' ||
                    buffer.get(buffer.size() - 1) == ' ' ||buffer.get(buffer.size()-1) == '*' || !listEnded || inQuotation))) {
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
            //bufferString.append(buffer.get(buffer.size()-1));

            if (buffer.size() >= 2) {
                if (buffer.get(buffer.size() - 1) == '*' && buffer.get(buffer.size() - 2) == '*') {
                    //bold formatting parser
                    processWords(addNewParagraph, 2, buffer, listener);
                    if (!boldOpen) {
                        listener.beginFormat(Format.BOLD, Listener.EMPTY_PARAMETERS);
                        boldOpen = true;

                    } else {
                        listener.endFormat(Format.BOLD, Listener.EMPTY_PARAMETERS);
                        boldOpen = false;
                    }
                    continue;
                }
                if (buffer.get(buffer.size() -1) == ' ' && buffer.get(buffer.size() - 2) == '>') {
                    //process quotation.
                    if (buffer.size() - 2 > quotationIdentation) {
                        while (quotationIdentation < buffer.size() -2) {
                            listener.beginQuotation(Listener.EMPTY_PARAMETERS);
                            listener.beginQuotationLine();
                            quotationIdentation++;
                        }
                    } else if (buffer.size() - 2 < quotationIdentation) {
                        while(quotationIdentation > (buffer.size() -2)) {
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
                if ( buffer.get(buffer.size() - 1) == ' ' && buffer.get(buffer.size() - 2) == '*') {
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
                    processWords(addNewParagraph, 2, buffer, listener);
                    if (!italicOpen) {
                        listener.beginFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = true;
                    } else {
                        //generate italic close event
                        listener.endFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = false;
                    }
                    continue;
                }

                if (buffer.get(buffer.size() - 1) == '_' && buffer.get(buffer.size() - 2) == '_') {
                    //underline format parser
                    processWords(addNewParagraph, 2, buffer, listener);
                    if (!underlineOpen) {
                        listener.beginFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = true;
                    } else {
                        listener.endFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = false;
                    }
                    continue;
                }

                if (buffer.get(buffer.size() - 1) == '\'' && buffer.get(buffer.size() - 2) == '\'') {
                    //monospace format parser
                    processWords(addNewParagraph, 2, buffer, listener);
                    buffer.clear();
                    if (!monospaceOpen) {
                        listener.beginFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = true;
                    } else {
                        listener.endFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = false;
                    }
                    continue;
                }
            }

            if (buffer.size() > 0 && buffer.get(buffer.size() - 1) == '\n') {
                //handle lists
                if (!listEnded || inQuotation) {
                    onNewLineCharacter = false;
                }
                if (buffer.size() >= 0) {
                    processWords(addNewParagraph, 1, buffer, listener);
                }
                if (onNewLineCharacter) {
                    addNewParagraph = true;
                    onNewLineCharacter = false;
                    continue;
                } else if (listEnded && !inQuotation){
                    onNewLineCharacter = true;
                }
                listEnded = true;
                inQuotation = false;
                buffer.clear();
            }

            if (getStringRepresentation(buffer).endsWith("[[")) {
                //generate subscript event
                processWords(addNewParagraph, 2, buffer, listener);
                processLink(false, source, listener);
                buffer.clear();
                continue;
            }

            if (getStringRepresentation(buffer).endsWith("\\ ")) {
                //generate newline event
                processWords(addNewParagraph, 3, buffer, listener);
                listener.onNewLine();
                continue;
            }

            if (getStringRepresentation(buffer).endsWith("{{")) {

                processWords(addNewParagraph, 2, buffer, listener);
                //handle image input
                processImage(source, listener);
                buffer.clear();
                continue;
            }

            if (buffer.size() > 1 && buffer.get(buffer.size() - 2) == '<' && buffer.get(buffer.size() - 1) != ' ') {
                //email address
                char temp = buffer.get(buffer.size() - 1);
                processWords(addNewParagraph, 2, buffer, listener);
                buffer.add(temp);
                while (source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size() - 1) == '>') {
                        if (Arrays.asList(supportedTags).contains("<" + getStringRepresentation(buffer))) {
                            buffer.add(0, '<');
                        } else if (buffer.contains('@')) {
                            buffer.subList(buffer.size() - 1, buffer.size()).clear();
                            ResourceReference reference = new ResourceReference(getStringRepresentation(buffer), ResourceType.MAILTO);
                            listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
                            listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
                            buffer.clear();
                        }
                        break;
                    }
                }
            }
            if (getStringRepresentation(buffer).endsWith("<nowiki>")) {
                //override syntax
                processWords(addNewParagraph, 8, buffer, listener);
                while (source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size() - 1) == '>') {
                        buffer.subList(buffer.size() - 9, buffer.size()).clear();
                        listener.onVerbatim(getStringRepresentation(buffer), true, Listener.EMPTY_PARAMETERS);
                        buffer.clear();
                        break;
                    }
                }
            }

            if (getStringRepresentation(buffer).endsWith("%%")) {
                //Also override syntax (same as <nowiki>)
                processWords(addNewParagraph, 2, buffer, listener);
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

            if (getStringRepresentation(buffer).endsWith("<sub>")) {
                //generate subscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.SUBSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("</sub>")) {
                //generate subscript close event
                processWords(addNewParagraph, 6, buffer, listener);
                listener.endFormat(Format.SUBSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("<sup>")) {
                //generate superscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.SUPERSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("</sup>")) {
                //generate superscript close event
                processWords(addNewParagraph, 6, buffer, listener);
                listener.endFormat(Format.SUPERSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("<del>")) {
                //generate strikeout open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.STRIKEDOUT, Listener.EMPTY_PARAMETERS);
            }
            if (getStringRepresentation(buffer).endsWith("</del>")) {
                //generate strikeout open event
                processWords(addNewParagraph, 6, buffer, listener);
                listener.endFormat(Format.STRIKEDOUT, Listener.EMPTY_PARAMETERS);
            }
        }
        //parse remaining as strings
        if (buffer.size() > 0) {
            processWords(addNewParagraph, 0, buffer, listener);
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

    private void processWords(boolean addNewParagraph, int argumentTrimSize, ArrayList<Character> buffer, Listener listener) {
        buffer.subList(buffer.size() - argumentTrimSize, buffer.size()).clear();
        StringBuilder word = new StringBuilder();
//        if (addNewParagraph) {
//            listener.endParagraph(Listener.EMPTY_PARAMETERS);
//            listener.beginParagraph(Listener.EMPTY_PARAMETERS);
//        }
        for (char c : buffer) {
            if (c == ' ' && word.length() == 0) {
                listener.onSpace();
            } else if (Arrays.asList(specialSymbols).contains(c)) {
                listener.onSpecialSymbol(c);
            } else if (c == ' ') {
                listener.onWord(word.toString());
                listener.onSpace();
                word.setLength(0);
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) {
            listener.onWord(word.toString());
        }
        buffer.clear();
    }

    private void processWordsFromReader(boolean addNewParagraph, Reader source, Listener listener, String endString) throws IOException {
        ArrayList<Character> buffer = new ArrayList<>();
        while (source.ready()) {
            buffer.add((char) source.read());
            String readString = getStringRepresentation(buffer);
            if (readString.endsWith(endString)) {
                buffer.subList(buffer.size() - endString.length(), buffer.size()).clear();
                //StringBuilder here has no utility.
                processWords(addNewParagraph, 0, buffer, listener);
                break;
            }
        }
    }

    private void processImage(Reader source, Listener listener) throws IOException {
        StringBuilder imageNameBuilder = new StringBuilder();
        while (source.ready()) {
            imageNameBuilder.append((char) source.read());
            String imageArgument = imageNameBuilder.toString();
            boolean internalImage = true;
            if (imageArgument.endsWith("}}")) {
                if (!imageArgument.contains("wiki:")) {
                    internalImage = false;
                }
                if (imageArgument.startsWith("{{ ") && imageArgument.endsWith(" }}")) {
                    //align centre
                    if (internalImage) {
                        String imageName = imageArgument.substring(5, imageArgument.length() - 3);
                        //listener.onImage(new ResourceReference(imageName, ResourceType.URL), false,  );
                    }
                } else if (imageArgument.startsWith(" ")) {
                    //align left
                } else if (imageArgument.endsWith(" }}")) {
                    //align right
                } else {
                    //no alignment info
                    String imageName;
                    if (internalImage) {
                        imageName = imageArgument.substring(5, imageArgument.length() - 2);

                    } else {
                        imageName = imageArgument.substring(1, imageArgument.length() - 2);
                    }
                    ResourceReference reference = new ResourceReference(imageName, ResourceType.URL);
                    reference.setTyped(false);
                    listener.onImage(reference, false, Listener.EMPTY_PARAMETERS);
                }
                break;
            }
        }
    }

    private void processLink(boolean addNewParagraph, Reader source, Listener listener) throws IOException {
        StringBuilder LinkBuilder = new StringBuilder();
        while (source.ready()) {
            LinkBuilder.append((char) source.read());
            String linkArgument = LinkBuilder.toString();
            // boolean internalLink = true;
            if (linkArgument.endsWith("|")) {
                ResourceReference reference = new ResourceReference(linkArgument.substring(0, linkArgument.length() - 1), ResourceType.URL);
                reference.setTyped(false);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                processWordsFromReader(addNewParagraph, source, listener, "]]");
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }
            if (linkArgument.endsWith("]]")) {
                ResourceReference reference = new ResourceReference(linkArgument.substring(0, linkArgument.length() - 2), ResourceType.URL);
                reference.setTyped(false);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }
        }
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