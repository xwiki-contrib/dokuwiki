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
    private static String[] supportedTags = new String[] {"<del>", "</del>", "<sub>", "</sub>", "<sup>", "</sup>", "<nowiki>", "</nowiki>"};

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
        boolean addNewParagraph = false;
        boolean onNewLineCharacter = false;
        boolean horizontalLineAdded = false;
        int readCharacter;
        boolean boldOpen = false;
        boolean italicOpen = false;
        boolean underlineOpen = false;
        boolean monospaceOpen = false;
        boolean linkOpen = true;
        int spaceIndentationUnOrdered  = -1;
        int quotationLevel = -1;
        boolean inQuotation = false;
        boolean listEnded = true;

        listener.beginParagraph(Listener.EMPTY_PARAMETERS);

        while (source.ready() && (readCharacter = source.read()) != -1 ) {
            buffer.add((char) readCharacter);
            if (getStringRepresentation(buffer).endsWith("----")) {
                //end paragraph
                listener.endParagraph(Listener.EMPTY_PARAMETERS);
                //generate newline event
                listener.onHorizontalLine(Listener.EMPTY_PARAMETERS);
                //start paragraph
                listener.beginParagraph(Listener.EMPTY_PARAMETERS);
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

            if (addNewParagraph) {
                if (!(buffer.size() > 0 && (buffer.get(buffer.size() - 1) == '-' || buffer.get(buffer.size() - 1) == ' '))) {
                    listener.endParagraph(Listener.EMPTY_PARAMETERS);
                    listener.beginParagraph(Listener.EMPTY_PARAMETERS);
                    addNewParagraph = false;
                }
            }
            horizontalLineAdded  =false;
            //bufferString.append(buffer.get(buffer.size()-1));
            if (buffer.size() > 0 && buffer.get(buffer.size()-1) == '>') {
                //process quotation.
                inQuotation = true;
                processWords(addNewParagraph, 1, buffer, listener);
                buffer.add((char) source.read());
                int currentQuotationLevel = 0;
                while(buffer.get(buffer.size()-1) !=  ' ') {
                    currentQuotationLevel++;
                    buffer.add((char) source.read());
                }
                if (currentQuotationLevel > quotationLevel) {
                    while(currentQuotationLevel > quotationLevel) {
                        listener.beginQuotation(Listener.EMPTY_PARAMETERS);
                        quotationLevel++;
                    }
                } else if (currentQuotationLevel < quotationLevel) {
                    while(currentQuotationLevel < quotationLevel) {
                        listener.endQuotation(Listener.EMPTY_PARAMETERS);
                        quotationLevel--;
                    }
                }
                listener.beginQuotationLine();
                buffer.clear();
                continue;
            }

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
                if (buffer.get(buffer.size() - 1) == ' ' && buffer.get(buffer.size() - 2) == '*') {
                    //unordered list
                    buffer.subList(buffer.size() - 2, buffer.size()).clear();
                    listEnded = false;

                    if (buffer.size() > spaceIndentationUnOrdered) {
                        listener.beginList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
                        spaceIndentationUnOrdered++;
                    } else if (buffer.size() < spaceIndentationUnOrdered) {
                        listener.endList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
                        spaceIndentationUnOrdered--;
                    } else {
                        listener.beginListItem();
                    }
                    buffer.subList(buffer.size() - spaceIndentationUnOrdered, buffer.size()).clear();
                    continue;
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

            if (buffer.size() >0 && buffer.get(buffer.size()-1) == '\n') {
                //handle lists
                if (buffer.size() >= 0) {
                    processWords(addNewParagraph, 1, buffer, listener);
                }
                if (spaceIndentationUnOrdered >= 0 && !listEnded) {
                    listener.endListItem();
                    listEnded = true;
                } else if(spaceIndentationUnOrdered >=0 && listEnded) {
                    while (spaceIndentationUnOrdered >= 0) {
                        listener.endList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
                        spaceIndentationUnOrdered--;
                    }
                }else if (quotationLevel >= 0 && inQuotation) {
                    //handle quotation input.
                    listener.endQuotationLine();
                    inQuotation = false;
                } else if (quotationLevel >= 0) {
                    while (quotationLevel >=0) {
                        listener.endQuotation(Listener.EMPTY_PARAMETERS);
                        quotationLevel--;
                    }
                } else if (onNewLineCharacter){
                    addNewParagraph =true;
                    onNewLineCharacter = false;
                    continue;
                } else {
                    onNewLineCharacter = true;
                }
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

            if (buffer.size() >0 && buffer.get(buffer.size()-1) == '<') {
                //email address
                processWords(addNewParagraph, 1, buffer, listener);
                while(source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size()-1) == '>') {
                        if (Arrays.asList(supportedTags).contains("<" + getStringRepresentation(buffer))) {
                            buffer.add(0, '<');
                        } else if (buffer.contains('@')) {
                            buffer.subList(buffer.size() - 1,buffer.size()).clear();
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
                while(source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size()-1) == '>') {
                        buffer.subList(buffer.size() - 9,buffer.size()).clear();
                        listener.onVerbatim(getStringRepresentation(buffer), true, Listener.EMPTY_PARAMETERS);
                        buffer.clear();
                        break;
                    }
                }
            }

            if (getStringRepresentation(buffer).endsWith("%%")) {
                //Also override syntax (same as <nowiki>)
                processWords(addNewParagraph, 2, buffer, listener);
                while(source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size()-1) == '%' &&buffer.get(buffer.size()-2) == '%' ) {
                        buffer.subList(buffer.size() - 2,buffer.size()).clear();
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
            if (!inQuotation && quotationLevel >=0) {
                while (quotationLevel >=0) {
                    listener.endQuotation(Listener.EMPTY_PARAMETERS);
                }
            }
        }
        //parse remaining as strings
        if (buffer.size() > 0) {
            processWords(addNewParagraph, 0, buffer, listener);
        }
        listener.endParagraph(Listener.EMPTY_PARAMETERS);

    }

    private void processWords(boolean addNewParagraph,  int argumentTrimSize, ArrayList<Character> buffer, Listener listener) {
        buffer.subList(buffer.size() - argumentTrimSize, buffer.size()).clear();
        StringBuilder word = new StringBuilder();
//        if (addNewParagraph) {
//            listener.endParagraph(Listener.EMPTY_PARAMETERS);
//            listener.beginParagraph(Listener.EMPTY_PARAMETERS);
//        }
        for (char c : buffer) {
            if (c == ' ' && word.length() == 0) {
                listener.onSpace();
            } else  if (c == ' ') {
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
        while(source.ready()) {
            buffer.add((char) source.read());
            String readString = getStringRepresentation(buffer);
            if (readString.endsWith(endString)) {
                buffer.subList(buffer.size() - endString.length(),buffer.size()).clear();
                //StringBuilder here has no utility.
                processWords(addNewParagraph, 0, buffer, listener);
                break;
            }
        }
    }

    private void processImage(Reader source, Listener listener) throws IOException {
        StringBuilder imageNameBuilder = new StringBuilder();
        while(source.ready()) {
            imageNameBuilder.append((char) source.read());
            String imageArgument = imageNameBuilder.toString();
            boolean internalImage= true;
            if(imageArgument.endsWith("}}")) {
                if (!imageArgument.contains("wiki:")) {
                    internalImage = false;
                }
                if (imageArgument.startsWith("{{ ") && imageArgument.endsWith(" }}")) {
                    //align centre
                    if (internalImage) {
                        String imageName = imageArgument.substring(5, imageArgument.length() -3);
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
                        imageName = imageArgument.substring(5, imageArgument.length() -2);

                    } else {
                        imageName = imageArgument.substring(1, imageArgument.length() -2);
                    }
                    ResourceReference reference = new ResourceReference(imageName, ResourceType.URL);
                    reference.setTyped(false);
                    listener.onImage(reference, false, Listener.EMPTY_PARAMETERS);
                }
                break;
            }
        }
    }

    private void processLink(boolean addNewParagraph, Reader source, Listener  listener) throws IOException {
        StringBuilder LinkBuilder = new StringBuilder();
        while(source.ready()) {
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