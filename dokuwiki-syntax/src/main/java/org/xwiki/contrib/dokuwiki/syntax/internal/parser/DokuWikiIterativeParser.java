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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ParseException;

import static java.lang.Math.abs;

class DokuWikiIterativeParser
{
    private static final String TAG_ALIGN = "align";

    private static final String TAG_DOUBLE_CLOSING_SQUARE_BRACKETS = "]]";

    private static final String TAG_SPACE_DOUBLE_CLOSING_CURLY_BRACKETS = " }}";

    private static final String TAG_WIDTH = "width";

    private static final String TAG_PX = "px";

    private static final String TAG_X = "x";

    private static final String TAG_RIGHT = "right";

    private static final String TAG_LEFT = "left";

    private static final String TAG_HTML = "html";

    private static final String TAG_PHP = "php";

    private static final String TAG_RSS_GREATER_THAN_SYMBOL = "rss>";

    private static final String TAG_CODE = "code";

    private static Character[] specialSymbols =
            new Character[]{ '@', '#', '$', '*', '%', '\'', '(', '!', ')', '-', '_', '^', '`', '?', ',', ';',
                    '.', '/', ':', '=', '+', '<', '|', '>' };

    //Paragraph event just called, but no word in it
    private boolean paragraphJustOpened = false;

    @Inject
    private Logger logger;

    void parse(Reader source, Listener listener, MetaData metaData) throws ParseException
    {
        try {
            listener.beginDocument(metaData);
            parseRecursive(source, listener);
            listener.endDocument(metaData);
        } catch (IOException e) {
            throw new ParseException("Failed to parse input");
        }
    }

    private void parseRecursive(Reader source, Listener listener) throws IOException
    {
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
        int listSpaceidentation = -1;
        int quotationIdentation = -1;
        boolean inQuotation = false;
        boolean listEnded = true;
        boolean inSectionEvent = false;
        int headerLevel = 1;
        boolean inCodeBlock;
        while (source.ready()) {
            readCharacter = source.read();
            if (readCharacter == -1) {
                break;
            }
            buffer.add((char) readCharacter);
            if (getStringRepresentation(buffer).endsWith("----")) {
                //generate newline event
                if (inParagraph) {
                    listener.endParagraph(Listener.EMPTY_PARAMETERS);
                    inParagraph = false;
                }
                listener.onHorizontalLine(Listener.EMPTY_PARAMETERS);
                buffer.clear();
                horizontalLineAdded = true;
                continue;
            }
            //remove unnecessary new line characters.
            if (buffer.size() > 0 && buffer.get(buffer.size() - 1) == '\n' && (addNewParagraph)) {
                buffer.subList(buffer.size() - 1, buffer.size()).clear();
                continue;
            }

            if (!(buffer.size() > 0 && (buffer.get(buffer.size() - 1) == '-' || buffer.get(buffer.size() - 1) == '>'
                    || (buffer.contains('<') && !buffer.contains('@'))
                    || buffer.get(buffer.size() - 1) == ' ' || buffer.get(buffer.size() - 1) == '*'
                    || buffer.get(buffer.size() - 1) == '=' || buffer.get(buffer.size() - 1) == '<'
                    || buffer.get(buffer.size() - 1) == '^' || !listEnded || inQuotation
                    || inSectionEvent || horizontalLineAdded)))
            {
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
                        this.paragraphJustOpened = true;
                        inParagraph = true;
                    }
                    addNewParagraph = false;
                } else if (addNewParagraph) {
                    listener.endParagraph(Listener.EMPTY_PARAMETERS);
                    listener.beginParagraph(Listener.EMPTY_PARAMETERS);
                    this.paragraphJustOpened = true;
                    addNewParagraph = false;
                } else if (onNewLineCharacter && (((char) readCharacter) != '\n')
                        && !this.paragraphJustOpened)
                {
                    //add space after single new line character
                    listener.onSpace();
                    onNewLineCharacter = false;
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
                if (getStringRepresentation(buffer).endsWith("==")) {
                    if (!inSectionEvent) {
                        inSectionEvent = true;
                        buffer.clear();
                    } else if (inSectionEvent) {
                        int headerLevelAdjusted = headerLevel;
                        listener.beginSection(Listener.EMPTY_PARAMETERS);
                        //Above 5 isn't supported.
                        if (headerLevelAdjusted > 5) {
                            headerLevelAdjusted = 5;
                        }
                        //Dokuwiki doesn't use ids in headers
                        listener.beginHeader(HeaderLevel.parseInt(abs(6 - headerLevelAdjusted)), "",
                                Listener.EMPTY_PARAMETERS);
                        processWords(2, buffer, listener);
                        listener.endHeader(HeaderLevel.parseInt(abs(6 - headerLevelAdjusted)), "",
                                Listener.EMPTY_PARAMETERS);
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
                    while (source.ready()) {
                        c = source.read();
                        if (c == -1) {
                            break;
                        }
                        if (((char) c) == '\n') {
                            listener.onMacro(TAG_CODE, Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                                    false);
                            buffer.clear();
                            endOfLine = true;
                            break;
                        } else {
                            buffer.add((char) c);
                        }
                    }
                    if (!endOfLine) {
                        listener.onMacro(TAG_CODE, Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                                false);
                        buffer.clear();
                    }
                    continue;
                }
                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == '*'
                        && buffer.get(buffer.size() - 2) == '*')
                {
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
                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == '('
                        && buffer.get(buffer.size() - 2) == '(')
                {
                    //beginning of footnote
                    processWords(2, buffer, listener);
                    continue;
                }
                if (buffer.get(buffer.size() - 1) == ')' && buffer.get(buffer.size() - 2) == ')') {
                    //ending of footnote
                    buffer.subList(buffer.size() - 2, buffer.size()).clear();
                    listener.onMacro("foootnote", Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                            true);
                    buffer.clear();
                    continue;
                }

                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == ' '
                        && buffer.get(buffer.size() - 2) == '>')
                {
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
                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == ' '
                        && buffer.get(buffer.size() - 2) == '*')
                {
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

                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == ' '
                        && buffer.get(buffer.size() - 2) == '-')
                {
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

                if (getStringRepresentation(buffer).endsWith(" //") || getStringRepresentation(buffer).equals("//")) {
                    //handled separately to avoid collision with hyperlinks
                    //italics format open
                    if (!italicOpen) {
                        processWords(2, buffer, listener);
                        listener.beginFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = true;
                        continue;
                    }
                }

                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == '/'
                        && buffer.get(buffer.size() - 2) == '/')
                {
                    //Italics format parser close
                    if (italicOpen) {
                        //generate italic close event
                        processWords(2, buffer, listener);
                        listener.endFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = false;
                        continue;
                    }
                }

                if (getStringRepresentation(buffer).endsWith(" __")
                        || getStringRepresentation(buffer).equals("__"))
                {
                    //Underline open
                    if (!underlineOpen) {
                        processWords(2, buffer, listener);
                        listener.beginFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = true;
                        continue;
                    }
                }

                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == '_'
                        && buffer.get(buffer.size() - 2) == '_')
                {
                    //underline close
                    if (underlineOpen) {
                        processWords(2, buffer, listener);
                        listener.endFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = false;
                        continue;
                    }
                }

                if (getStringRepresentation(buffer).endsWith(" \'\'")
                        || getStringRepresentation(buffer).equals("\'\'"))
                {
                    //monospace open
                    if (!monospaceOpen) {
                        processWords(2, buffer, listener);
                        listener.beginFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = true;
                        continue;
                    }
                }

                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == '\''
                        && buffer.get(buffer.size() - 2) == '\'')
                {
                    //monospace close
                    if (monospaceOpen) {
                        processWords(2, buffer, listener);
                        listener.endFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = false;
                        continue;
                    }
                }
            }

            if (getStringRepresentation(buffer).endsWith("\\ ")
                    || getStringRepresentation(buffer).endsWith("\\\n"))
            {
                //generate newline event
                processWords(3, buffer, listener);
                listener.onNewLine();
                continue;
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

            if (getStringRepresentation(buffer).endsWith("{{")) {

                processWords(2, buffer, listener);
                //handle media input
                processImage(buffer, source, listener);
            }
            if (buffer.size() > 0 && (buffer.get(0) == '|' || buffer.get(0) == '^')) {
                boolean inCell = false;
                boolean inHeadCell = false;
                boolean inRow = false;
                boolean inTable;
                listener.beginTable(Listener.EMPTY_PARAMETERS);
                inTable = true;
                int cInt;
                while (source.ready()) {
                    cInt = source.read();
                    if (cInt == -1) {
                        break;
                    }
                    if (buffer.get(buffer.size() - 1) == '|') {
                        if (!inRow) {
                            listener.beginTableRow(Listener.EMPTY_PARAMETERS);
                            inRow = true;
                        }
                        processTableCell(inCell, inHeadCell, buffer, listener);
                        inHeadCell = false;
                        inCell = true;
                        buffer.clear();
                    } else if (buffer.get(buffer.size() - 1) == '^') {
                        if (!inRow) {
                            listener.beginTableRow(Listener.EMPTY_PARAMETERS);
                            inRow = true;
                        }
                        processTableCell(inCell, inHeadCell, buffer, listener);
                        inCell = false;
                        inHeadCell = true;
                        buffer.clear();
                    } else if (buffer.get(0) == '\n') {
                        listener.endTableRow(Listener.EMPTY_PARAMETERS);
                        buffer.clear();
                        inRow = false;
                        inCell = false;
                        inHeadCell = false;
                        if (cInt != '|' && cInt != '^') {
                            buffer.clear();
                            listener.endTable(Listener.EMPTY_PARAMETERS);
                            buffer.add((char) cInt);
                            inTable = false;
                            break;
                        }
                    }
                    buffer.add((char) cInt);
                }
                if (inTable) {
                    if (inCell || inHeadCell) {
                        processTableCell(inCell, inHeadCell, buffer, listener);
                    }
                    buffer.clear();
                    listener.endTableRow(Listener.EMPTY_PARAMETERS);
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
                char[] noWikiTag = new char[]{ 'n', 'o', 'w', 'i', 'k', 'i', '>' };
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
                            listener.onVerbatim(getStringRepresentation(buffer), inParagraph,
                                    Listener.EMPTY_PARAMETERS);
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
                        listener.onVerbatim(getStringRepresentation(buffer), true,
                                Listener.EMPTY_PARAMETERS);
                        buffer.clear();
                        break;
                    }
                }
            }

            if (getStringRepresentation(buffer).startsWith("<") && getStringRepresentation(buffer).endsWith(">")
                    && getStringRepresentation(buffer).contains("@"))
            {
                //email address
                processEmailAddressFromBuffer(buffer, listener);
                continue;
            }
            if (getStringRepresentation(buffer).equals("~~NOTOC~~")) {
                //disable table of content
                processWords(9, buffer, listener);
                //disable toc
                continue;
            }
            if (getStringRepresentation(buffer).equals("~~NOCACHE~~")) {
                //TODO Disable cache
                processWords(11, buffer, listener);
                continue;
            }
            if (getStringRepresentation(buffer).equals(TAG_RSS_GREATER_THAN_SYMBOL)) {
                //handle RSS generation feeds
                processWords(4, buffer, listener);
                int c;
                Map<String, String> param = new HashMap<>();
                while (source.ready()) {
                    c = source.read();
                    if (c == -1) {
                        break;
                    }
                    buffer.add((char) c);
                    if (c == '}') {
                        String[] argument = getStringRepresentation(buffer).split("\\s");
                        param.put("feed", argument[0]);
                        param.put("count", "8");
                        if (Arrays.asList(argument).contains("description")) {
                            param.put("content", "true");
                        }
                        listener.onMacro("rss", param, null, true);
                        //remove remaining curly bracket
                        source.read();
                        break;
                    }
                }
                buffer.clear();
                continue;
            }

            if (getStringRepresentation(buffer).startsWith("<php>")) {
                processWords(5, buffer, listener);
                int c;
                while (source.ready()) {
                    c = source.read();
                    if (c == -1) {
                        break;
                    }
                    buffer.add((char) c);
                    if (getStringRepresentation(buffer).endsWith("</php>")) {
                        buffer.subList(buffer.size() - 6, buffer.size()).clear();
                        listener.onMacro(TAG_PHP, Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                                true);
                        break;
                    }
                }
                buffer.clear();
                continue;
            }
            if (getStringRepresentation(buffer).startsWith("<PHP>")) {
                processWords(5, buffer, listener);
                int c;
                while (source.ready()) {
                    c = source.read();
                    if (c == -1) {
                        break;
                    }
                    buffer.add((char) c);
                    if (getStringRepresentation(buffer).endsWith("</PHP>")) {
                        buffer.subList(buffer.size() - 6, buffer.size()).clear();
                        listener.onMacro(TAG_PHP, Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                                false);
                        break;
                    }
                }
                buffer.clear();
                continue;
            }
            if (getStringRepresentation(buffer).startsWith("<html>")) {
                //html inline macro
                processWords(6, buffer, listener);
                int c = source.read();
                while (source.ready() && c != -1) {
                    buffer.add((char) c);
                    if (getStringRepresentation(buffer).endsWith("</html>")) {
                        buffer.subList(buffer.size() - 7, buffer.size()).clear();
                        listener.onMacro(TAG_HTML, Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                                true);
                        break;
                    }
                    c = source.read();
                }
                buffer.clear();
                continue;
            }
            if (getStringRepresentation(buffer).startsWith("<HTML>")) {
                //html block macro
                processWords(6, buffer, listener);
                int c = source.read();
                while (source.ready() && c != -1) {
                    buffer.add((char) c);
                    if (getStringRepresentation(buffer).endsWith("</HTML>")) {
                        buffer.subList(buffer.size() - 7, buffer.size()).clear();
                        listener.onMacro(TAG_HTML, Listener.EMPTY_PARAMETERS, getStringRepresentation(buffer),
                                false);
                        break;
                    }
                    c = source.read();
                }
                buffer.clear();
                continue;
            }

            if (getStringRepresentation(buffer).endsWith("<code ")
                    || getStringRepresentation(buffer).endsWith("<code>")
                    || getStringRepresentation(buffer).endsWith("<file ")
                    || getStringRepresentation(buffer).endsWith("<file>"))
            {
                //handle code block
                String language;
                int c;
                boolean readLangauge = false;
                Map<String, String> param = new HashMap<>();
                inCodeBlock = true;
                if (buffer.get(5) != '>') {
                    readLangauge = true;
                }
                processWords(6, buffer, listener);

                //read language and code
                c = source.read();
                while (source.ready() && c != -1) {
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

                    if (getStringRepresentation(buffer).endsWith("</code>")
                            || getStringRepresentation(buffer).endsWith("</file>"))
                    {
                        if (buffer.contains('\n')) {
                            buffer.subList(buffer.size() - 8, buffer.size()).clear();
                        } else {
                            buffer.subList(buffer.size() - 7, buffer.size()).clear();
                        }
                        if (inCodeBlock) {
                            listener.onMacro(TAG_CODE, param, getStringRepresentation(buffer), false);
                            inCodeBlock = false;
                        }
                        buffer.clear();
                        //consume a newLine character.
                        source.read();
                        break;
                    }
                    c = source.read();
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

    private void processWords(int argumentTrimSize, ArrayList<Character> buffer, Listener listener)
    {
        buffer.subList(buffer.size() - argumentTrimSize, buffer.size()).clear();
        StringBuilder word = new StringBuilder();
        boolean spaceAdded = false;
        for (char c : buffer) {
            if (c == ' ') {
                if (!spaceAdded) {
                    if (word.length() > 0) {
                        processWord(word, listener);
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
            processWord(word, listener);
        }
        buffer.clear();
    }

    private void processWord(StringBuilder word, Listener listener)
    {
        if (this.paragraphJustOpened) {
            this.paragraphJustOpened = false;
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
    }

    private void processWordsFromReader(char ch, Reader source, Listener listener, String endString) throws IOException
    {
        ArrayList<Character> buffer = new ArrayList<>();
        buffer.add(ch);
        int c = source.read();
        while (source.ready() && c != -1) {
            buffer.add((char) c);
            String readString = getStringRepresentation(buffer);
            if (readString.endsWith(endString)) {
                buffer.subList(buffer.size() - endString.length(), buffer.size()).clear();
                //StringBuilder here has no utility.
                processWords(0, buffer, listener);
                break;
            }
            c = source.read();
        }
    }

    private void processImage(
            ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException
    {
        int c = source.read();
        while (source.ready() && c != -1) {
            buffer.add((char) c);
            String imageArgument = getStringRepresentation(buffer);
            boolean internalImage = true;
            Map<String, String> param = new HashMap<>();
            if (imageArgument.startsWith(TAG_RSS_GREATER_THAN_SYMBOL)) {
                break;
            }
            if (imageArgument.endsWith("}}")) {
                String imageName;
                if (!imageArgument.contains("wiki:")) {
                    internalImage = false;
                }
                if (imageArgument.startsWith("{{ ")
                        && imageArgument.endsWith(TAG_SPACE_DOUBLE_CLOSING_CURLY_BRACKETS))
                {
                    //align centre
                    param.put(TAG_ALIGN, "middle");
                } else if (imageArgument.startsWith(" ")) {
                    //align left
                    param.put(TAG_ALIGN, TAG_LEFT);
                } else if (imageArgument.endsWith(TAG_SPACE_DOUBLE_CLOSING_CURLY_BRACKETS)) {
                    //align right
                    param.put(TAG_ALIGN, TAG_RIGHT);
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
                    if (size.contains(TAG_X)) {
                        param.put("height", size.substring(0, size.indexOf(TAG_X)) + TAG_PX);
                        param.put(TAG_WIDTH, size.substring(size.indexOf(TAG_X) + 1) + TAG_PX);
                    } else {
                        param.put(TAG_WIDTH, size + TAG_PX);
                    }
                }
                ResourceReference reference = new ResourceReference(imageName, ResourceType.URL);
                reference.setTyped(false);
                listener.onImage(reference, false, param);
                buffer.clear();
                break;
            }
            c = source.read();
        }
    }

    private void processLink(Reader source, Listener listener) throws IOException
    {
        int c;
        ArrayList<Character> functionBuffer = new ArrayList<>();
        c = source.read();
        while (source.ready() && c != -1) {
            ResourceReference reference;
            functionBuffer.add((char) c);
            if ((char) c == '|') {
                c = (char) source.read();
                if (functionBuffer.get(0) == '<' && functionBuffer.get(functionBuffer.size() - 2) == '>'
                        && functionBuffer.contains('@'))
                {
                    //process mailto
                    reference = new ResourceReference(getStringRepresentation(
                            new ArrayList<>(functionBuffer
                                    .subList(1, functionBuffer.size() - 2))), ResourceType.MAILTO);
                    reference.setTyped(true);
                    listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                    processWordsFromReader((char) c, source, listener, TAG_DOUBLE_CLOSING_SQUARE_BRACKETS);
                    listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                    break;
                }
                else {
                    if (c != '{'){
                        reference = new ResourceReference(getStringRepresentation(
                                new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() - 1))), ResourceType.URL);
                        reference.setTyped(false);
                        listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                        functionBuffer.add((char) c);
                        processWordsFromReader((char) c, source, listener, TAG_DOUBLE_CLOSING_SQUARE_BRACKETS);
                        listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                        break;
                    } else {
                        functionBuffer.add((char) c);
                    }
                }
            }

            if (getStringRepresentation(functionBuffer).endsWith("{{")) {
                reference = new ResourceReference(getStringRepresentation(new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() - 3))), ResourceType.URL);
                reference.setTyped(false);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                functionBuffer.clear();
                processImage(functionBuffer, source, listener);
                source.skip(2);
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }

            if (getStringRepresentation(functionBuffer).endsWith(TAG_DOUBLE_CLOSING_SQUARE_BRACKETS)) {
                reference = new ResourceReference(getStringRepresentation(
                        new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() - 2))), ResourceType.URL);
                reference.setTyped(false);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }
            c = source.read();
        }
    }

    private void processTableCell(boolean inCell, boolean inHeadCell, ArrayList<Character> buffer, Listener listener)
    {
        //call the cell events
        buffer.remove(buffer.size() - 1);
        Map<String, String> param = new HashMap<>();
        if (buffer.size() > 2 && buffer.get(0) == ' ' && buffer.get(buffer.size() - 1) == ' ') {
            param.put(TAG_ALIGN, "centre");
        } else if (buffer.size() > 1 && buffer.get(0) == ' ') {
            param.put(TAG_ALIGN, TAG_RIGHT);
        } else if (buffer.size() > 1 && buffer.get(buffer.size() - 1) == ' ') {
            param.put(TAG_ALIGN, TAG_LEFT);
        }
        if (inCell) {
            trimBuffer(buffer);
            listener.beginTableCell(param);
            processWords(0, buffer, listener);
            listener.endTableCell(param);
        }
        if (inHeadCell) {
            trimBuffer(buffer);
            listener.beginTableHeadCell(param);
            processWords(0, buffer, listener);
            listener.endTableHeadCell(param);
        }
    }

    private void processEmailAddressFromBuffer(ArrayList<Character> buffer, Listener listener)
    {
        buffer.remove(0);
        buffer.remove(buffer.size() - 1);
        ResourceReference reference = new ResourceReference(getStringRepresentation(buffer), ResourceType.MAILTO);
        listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
        listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
        buffer.clear();
    }

    //generic helper methods
    private String getStringRepresentation(ArrayList<Character> list)
    {
        StringBuilder builder = new StringBuilder(list.size());
        for (Character ch : list) {
            builder.append(ch);
        }
        return builder.toString();
    }

    private boolean checkURL(String string)
    {
        String urlRegex = "^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$";
        Pattern p = Pattern.compile(urlRegex);
        Matcher m = p.matcher(string);
        return m.find();
    }

    private void trimBuffer(ArrayList<Character> buffer)
    {
        while (buffer.size() > 0 && buffer.get(0) == ' ') {
            buffer.remove(0);
        }
        while (buffer.size() > 0 && buffer.get(buffer.size() - 1) == ' ') {
            buffer.remove(buffer.size() - 1);
        }
    }
}