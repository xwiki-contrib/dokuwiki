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
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxParserHelper;
import org.xwiki.contrib.dokuwiki.syntax.plugins.DokuWikiPlugin;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.reference.InterWikiResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

import static java.lang.Math.abs;

@Component(roles = DokuWikiIterativeParser.class)
@Singleton
public class DokuWikiIterativeParser
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

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private DokuWikiSyntaxParserHelper helper;

    private static class ListState {
        private final int depth;

        private final ListType type;

        ListState(int depth, ListType type) {
            this.depth = depth;
            this.type = type;
        }

        int getDepth() {
            return depth;
        }

        ListType getType() {
            return type;
        }
    }

    void parse(Reader source, Listener listener, MetaData metaData)
            throws ComponentLookupException, IOException
    {
        listener.beginDocument(metaData);
        parseRecursive(source, listener);
        listener.endDocument(metaData);
    }

    private void parseRecursive(Reader originalSource, Listener listener) throws IOException, ComponentLookupException
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
        int initialListDepth = -1;
        Deque<ListState> listStack = new ArrayDeque<>();
        int quotationIndentation = -1;
        boolean inQuotation = false;
        boolean listEnded = true;
        boolean inSectionEvent = false;
        int headerLevel = 1;
        boolean inCodeBlock;
        List<DokuWikiPlugin> componentList = componentManagerProvider.get().getInstanceList(DokuWikiPlugin.class);

        // Use a PushbackReader to be able to look ahead at the next characters.
        PushbackReader source = new PushbackReader(originalSource, 3);

        while (source.ready()) {
            readCharacter = source.read();
            if (readCharacter == -1) {
                break;
            }
            buffer.add((char) readCharacter);

            if (componentList != null) {
                for (DokuWikiPlugin plugin : componentList)
                    plugin.parse(buffer, source, listener);
            }

            if (helper.getStringRepresentation(buffer).endsWith("----")) {
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
                    // Ignore buffers starting with two spaces as they will eventually be handled as code block or
                    // as list item.
                    || (buffer.size() > 1 && buffer.get(0) == ' ' && buffer.get(1) == ' ')
                    || buffer.get(buffer.size() - 1) == ' ' || buffer.get(buffer.size() - 1) == '*'
                    || buffer.get(buffer.size() - 1) == '=' || buffer.get(buffer.size() - 1) == '<'
                    || buffer.get(buffer.size() - 1) == '^' || !listEnded || inQuotation
                    || inSectionEvent || horizontalLineAdded)))
            {
                if (!inParagraph) {
                    if (!listStack.isEmpty() || quotationIndentation > -1) {
                        while (!listStack.isEmpty()) {
                            listener.endListItem();
                            closeList(listener, listStack);
                        }

                        while (quotationIndentation >= 0) {
                            listener.endQuotationLine();
                            listener.endQuotation(Listener.EMPTY_PARAMETERS);
                            quotationIndentation--;
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
                if (helper.getStringRepresentation(buffer).endsWith("==")) {
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
                        // TODO: generate heading ids, see https://jira.xwiki.org/browse/DOKUWIKI-28
                        listener.beginHeader(HeaderLevel.parseInt(abs(6 - headerLevelAdjusted)), "",
                                Listener.EMPTY_PARAMETERS);
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                        listener.endHeader(HeaderLevel.parseInt(abs(6 - headerLevelAdjusted)), "",
                                Listener.EMPTY_PARAMETERS);
                        listener.endSection(Listener.EMPTY_PARAMETERS);
                        // TODO: DokuWiki's parser is not picky about the number of '=' characters, two are enough so
                        //  this might swallow more than it should. See https://jira.xwiki.org/browse/DOKUWIKI-30
                        while (source.ready() && headerLevel > 1) {
                            source.read();
                            headerLevel--;
                        }
                        source.read();
                        inSectionEvent = false;
                    }
                    continue;
                }
                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == '*'
                        && buffer.get(buffer.size() - 2) == '*')
                {
                    //bold formatting parser
                    this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
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
                    this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                    continue;
                }
                if (buffer.get(buffer.size() - 1) == ')' && buffer.get(buffer.size() - 2) == ')') {
                    //ending of footnote
                    buffer.subList(buffer.size() - 2, buffer.size()).clear();
                    listener.onMacro("footnote", Listener.EMPTY_PARAMETERS, helper.getStringRepresentation(buffer),
                            true);
                    buffer.clear();
                    continue;
                }

                if (buffer.size() >= 2 && buffer.get(buffer.size() - 1) == ' '
                        && buffer.get(buffer.size() - 2) == '>')
                {
                    //process quotation.
                    if (buffer.size() - 2 > quotationIndentation) {
                        while (quotationIndentation < buffer.size() - 2) {
                            listener.beginQuotation(Listener.EMPTY_PARAMETERS);
                            listener.beginQuotationLine();
                            quotationIndentation++;
                        }
                    } else if (buffer.size() - 2 < quotationIndentation) {
                        while (quotationIndentation > (buffer.size() - 2)) {
                            listener.endQuotationLine();
                            listener.endQuotation(Listener.EMPTY_PARAMETERS);
                            quotationIndentation--;
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

                // If the buffer ends with * or - and contains at least two spaces or at least one tab (but not mixed)
                // before that, then it's a list.
                if (buffer.size() > 1 && (buffer.get(buffer.size() - 1) == '*' || buffer.get(buffer.size() - 1) == '-')
                    && (buffer.stream().filter(c -> c == '\t').count() == buffer.size() - 1
                    || buffer.stream().filter(c -> c == ' ').count() == buffer.size() - 1))
                {
                    // Consume the next character if it is a space, otherwise unread it. DokuWiki doesn't require a
                    // space at the start of the list item but there is usually one and XWiki preserves it (DokuWiki
                    // also preserves it, but browsers ignore spaces at the start of a block element).
                    int nextChar = source.read();
                    if (nextChar != -1 && nextChar != ' ') {
                        source.unread(nextChar);
                    }

                    String indentation = this.helper.getStringRepresentation(buffer.subList(0, buffer.size() - 1))
                        .replace("\t", "  ");
                    int listDepth = indentation.length() / 2;

                    ListType listType = buffer.get(buffer.size() - 1) == '*' ? ListType.BULLETED : ListType.NUMBERED;

                    if (listStack.isEmpty()) {
                        initialListDepth = listDepth;

                        listener.beginList(listType, Listener.EMPTY_PARAMETERS);
                        listener.beginListItem();
                        listStack.push(new ListState(listDepth, listType));
                    } else {
                        if (listDepth < initialListDepth) {
                            listDepth = initialListDepth;
                        }

                        if (listDepth > listStack.peek().getDepth()) {
                            listener.beginList(listType, Listener.EMPTY_PARAMETERS);
                            listener.beginListItem();
                            listStack.push(new ListState(listDepth, listType));
                        } else if (listDepth < listStack.peek().getDepth()) {
                            while (!listStack.isEmpty() && listDepth < listStack.peek().getDepth()) {
                                listener.endListItem();
                                closeList(listener, listStack);
                            }

                            listener.endListItem();

                            if (listType != listStack.peek().getType()) {
                                closeList(listener, listStack);
                                listener.beginList(listType, Listener.EMPTY_PARAMETERS);
                                listStack.push(new ListState(listDepth, listType));
                            }

                            listener.beginListItem();
                        } else {
                            listener.endListItem();

                            if (listType != listStack.peek().getType()) {
                                closeList(listener, listStack);
                                listener.beginList(listType, Listener.EMPTY_PARAMETERS);
                                listStack.push(new ListState(listDepth, listType));
                            }

                            listener.beginListItem();
                        }
                    }

                    listEnded = false;
                    buffer.clear();
                    continue;
                }

                // If the buffer starts with at least two spaces or at least one tab followed by another character,
                // it is a preformatted text.
                if ((buffer.size() > 1 && buffer.get(0) == '\t' && buffer.get(buffer.size() - 1) != '\t')
                    || (buffer.size() > 2 && buffer.get(0) == ' ' && buffer.get(1) == ' '
                    && buffer.get(buffer.size() - 1) != ' '))
                {
                    // Remove the indentation.
                    buffer.remove(0);
                    if (buffer.get(0) == ' ') {
                        buffer.remove(0);
                    }
                    
                    StringBuilder codeContent = new StringBuilder();

                    while (true) {
                        // Read into the buffer until the end of the line.
                        while (source.ready() && (buffer.isEmpty() || buffer.get(buffer.size() - 1) != '\n')) {
                            int c = source.read();
                            if (c == -1) {
                                break;
                            }
                            buffer.add((char) c);
                        }
                        
                        // Append the buffer to the code content.
                        buffer.forEach(codeContent::append);
                        buffer.clear();

                        // Start reading the next line. Read only the first character, if it is a space, also read
                        // the second character.
                        while (source.ready() && (buffer.isEmpty() || buffer.get(0) == ' ')
                            && buffer.size() < 2) {
                            int c = source.read();
                            if (c == -1) {
                                break;
                            }
                            buffer.add((char) c);
                        }
                        
                        // If the next line starts with a tab or two spaces, continue reading.
                        if (!buffer.isEmpty() && (buffer.get(0) == '\t' || (buffer.size() == 2
                            && buffer.get(0) == ' ' && buffer.get(1) == ' ')))
                        {
                            // Remove the indentation.
                            buffer.clear();
                        } else {
                            // Otherwise, end the code block.
                            // Remove the last character from code content if it is a newline.
                            if (codeContent.length() > 0 && codeContent.charAt(codeContent.length() - 1) == '\n') {
                                codeContent.deleteCharAt(codeContent.length() - 1);
                            }

                            listener.onMacro(TAG_CODE, Listener.EMPTY_PARAMETERS, codeContent.toString(), false);

                            // Push the characters in the buffer back to the source and clear the buffer.
                            while (!buffer.isEmpty()) {
                                source.unread(buffer.remove(buffer.size() - 1));
                            }

                            break;
                        }
                    }

                    continue;
                }

                if (helper.getStringRepresentation(buffer).endsWith(" //") || helper.getStringRepresentation(buffer)
                        .equals("//"))
                {
                    //handled separately to avoid collision with hyperlinks
                    //italics format open
                    if (!italicOpen) {
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
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
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                        listener.endFormat(Format.ITALIC, Listener.EMPTY_PARAMETERS);
                        italicOpen = false;
                        continue;
                    }
                }

                if (helper.getStringRepresentation(buffer).endsWith(" __")
                        || helper.getStringRepresentation(buffer).equals("__"))
                {
                    //Underline open
                    if (!underlineOpen) {
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
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
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                        listener.endFormat(Format.UNDERLINED, Listener.EMPTY_PARAMETERS);
                        underlineOpen = false;
                        continue;
                    }
                }

                if (helper.getStringRepresentation(buffer).endsWith(" \'\'")
                        || helper.getStringRepresentation(buffer).equals("\'\'"))
                {
                    //monospace open
                    if (!monospaceOpen) {
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
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
                        this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                        listener.endFormat(Format.MONOSPACE, Listener.EMPTY_PARAMETERS);
                        monospaceOpen = false;
                        continue;
                    }
                }
            }

            if (helper.getStringRepresentation(buffer).endsWith("\\ ")
                    || helper.getStringRepresentation(buffer).endsWith("\\\n"))
            {
                //generate newline event
                this.paragraphJustOpened = helper.processWords(3, buffer, listener, this.paragraphJustOpened);
                listener.onNewLine();
                continue;
            }

            if (buffer.size() > 0 && buffer.get(buffer.size() - 1) == '\n') {
                //handle lists
                if (!listEnded || inQuotation) {
                    onNewLineCharacter = false;
                }
                if (buffer.size() >= 0) {
                    this.paragraphJustOpened = helper.processWords(1, buffer, listener, this.paragraphJustOpened);
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

            if (helper.getStringRepresentation(buffer).endsWith("[[")) {
                //handle link
                this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                processLink(source, listener);
                buffer.clear();
                continue;
            }

            if (helper.getStringRepresentation(buffer).startsWith("{{")) {
                buffer.remove(0);
                buffer.remove(0);
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
                    this.paragraphJustOpened = helper.processWords(1, buffer, listener, this.paragraphJustOpened);
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
                            listener.onVerbatim(helper.getStringRepresentation(buffer), inParagraph,
                                    Listener.EMPTY_PARAMETERS);
                            buffer.clear();
                            break;
                        }
                    }
                }
            }

            if (helper.getStringRepresentation(buffer).endsWith("%%")) {
                //Also override syntax (same as <nowiki>) but is always inline
                this.paragraphJustOpened = helper.processWords(2, buffer, listener, this.paragraphJustOpened);
                while (source.ready()) {
                    buffer.add((char) source.read());
                    if (buffer.get(buffer.size() - 1) == '%' && buffer.get(buffer.size() - 2) == '%') {
                        buffer.subList(buffer.size() - 2, buffer.size()).clear();
                        listener.onVerbatim(helper.getStringRepresentation(buffer), true,
                                Listener.EMPTY_PARAMETERS);
                        buffer.clear();
                        break;
                    }
                }
            }

            if (helper.getStringRepresentation(buffer).startsWith("<") && helper.getStringRepresentation(buffer)
                    .endsWith(">")
                    && helper.getStringRepresentation(buffer).contains("@"))
            {
                //email address
                processEmailAddressFromBuffer(buffer, listener);
                continue;
            }
            if (helper.getStringRepresentation(buffer).equals("~~NOTOC~~")) {
                //disable table of content
                this.paragraphJustOpened = helper.processWords(9, buffer, listener, this.paragraphJustOpened);
                //disable toc
                continue;
            }
            if (helper.getStringRepresentation(buffer).equals("~~NOCACHE~~")) {
                //TODO Disable cache
                this.paragraphJustOpened = helper.processWords(11, buffer, listener, this.paragraphJustOpened);
                continue;
            }

            if (helper.getStringRepresentation(buffer).startsWith("<php>")) {
                this.paragraphJustOpened = helper.processWords(5, buffer, listener, this.paragraphJustOpened);
                int c;
                while (source.ready()) {
                    c = source.read();
                    if (c == -1) {
                        break;
                    }
                    buffer.add((char) c);
                    if (helper.getStringRepresentation(buffer).endsWith("</php>")) {
                        buffer.subList(buffer.size() - 6, buffer.size()).clear();
                        listener.onMacro(TAG_PHP, Listener.EMPTY_PARAMETERS, helper.getStringRepresentation(buffer),
                                true);
                        break;
                    }
                }
                buffer.clear();
                continue;
            }
            if (helper.getStringRepresentation(buffer).startsWith("<PHP>")) {
                this.paragraphJustOpened = helper.processWords(5, buffer, listener, this.paragraphJustOpened);
                int c;
                while (source.ready()) {
                    c = source.read();
                    if (c == -1) {
                        break;
                    }
                    buffer.add((char) c);
                    if (helper.getStringRepresentation(buffer).endsWith("</PHP>")) {
                        buffer.subList(buffer.size() - 6, buffer.size()).clear();
                        listener.onMacro(TAG_PHP, Listener.EMPTY_PARAMETERS, helper.getStringRepresentation(buffer),
                                false);
                        break;
                    }
                }
                buffer.clear();
                continue;
            }
            if (helper.getStringRepresentation(buffer).startsWith("<html>")) {
                //html inline macro
                this.paragraphJustOpened = helper.processWords(6, buffer, listener, this.paragraphJustOpened);
                int c = source.read();
                while (source.ready() && c != -1) {
                    buffer.add((char) c);
                    if (helper.getStringRepresentation(buffer).endsWith("</html>")) {
                        buffer.subList(buffer.size() - 7, buffer.size()).clear();
                        listener.onMacro(TAG_HTML, Listener.EMPTY_PARAMETERS, helper.getStringRepresentation(buffer),
                                true);
                        break;
                    }
                    c = source.read();
                }
                buffer.clear();
                continue;
            }
            if (helper.getStringRepresentation(buffer).startsWith("<HTML>")) {
                //html block macro
                this.paragraphJustOpened = helper.processWords(6, buffer, listener, this.paragraphJustOpened);
                int c = source.read();
                while (source.ready() && c != -1) {
                    buffer.add((char) c);
                    if (helper.getStringRepresentation(buffer).endsWith("</HTML>")) {
                        buffer.subList(buffer.size() - 7, buffer.size()).clear();
                        listener.onMacro(TAG_HTML, Listener.EMPTY_PARAMETERS, helper.getStringRepresentation(buffer),
                                false);
                        break;
                    }
                    c = source.read();
                }
                buffer.clear();
                continue;
            }

            if (helper.getStringRepresentation(buffer).endsWith("<code ")
                    || helper.getStringRepresentation(buffer).endsWith("<code>")
                    || helper.getStringRepresentation(buffer).endsWith("<file ")
                    || helper.getStringRepresentation(buffer).endsWith("<file>"))
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
                this.paragraphJustOpened = helper.processWords(6, buffer, listener, this.paragraphJustOpened);

                //read language and code
                c = source.read();
                while (source.ready() && c != -1) {
                    buffer.add((char) c);
                    if (readLangauge && ((char) c) == '>') {
                        if (buffer.contains(' ')) {
                            language = helper.getStringRepresentation(buffer).substring(0, buffer.indexOf(' ')).trim();
                        } else {
                            language = helper.getStringRepresentation(buffer).substring(0, buffer.size() - 1).trim();
                        }
                        param.put("language", language);
                        buffer.clear();
                        readLangauge = false;
                        //consume a newLine character.
                        source.read();
                    }

                    if (helper.getStringRepresentation(buffer).endsWith("</code>")
                            || helper.getStringRepresentation(buffer).endsWith("</file>"))
                    {
                        if (buffer.contains('\n')) {
                            buffer.subList(buffer.size() - 8, buffer.size()).clear();
                        } else {
                            buffer.subList(buffer.size() - 7, buffer.size()).clear();
                        }
                        if (inCodeBlock) {
                            listener.onMacro(TAG_CODE, param, helper.getStringRepresentation(buffer), false);
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

            if (helper.getStringRepresentation(buffer).endsWith("<sub>")) {
                //generate subscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.SUBSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (helper.getStringRepresentation(buffer).endsWith("</sub>")) {
                //generate subscript close event
                this.paragraphJustOpened = helper.processWords(6, buffer, listener, this.paragraphJustOpened);
                listener.endFormat(Format.SUBSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (helper.getStringRepresentation(buffer).endsWith("<sup>")) {
                //generate superscript open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.SUPERSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (helper.getStringRepresentation(buffer).endsWith("</sup>")) {
                //generate superscript close event
                this.paragraphJustOpened = helper.processWords(6, buffer, listener, this.paragraphJustOpened);
                listener.endFormat(Format.SUPERSCRIPT, Listener.EMPTY_PARAMETERS);
            }
            if (helper.getStringRepresentation(buffer).endsWith("<del>")) {
                //generate strikeout open event
                buffer.subList(buffer.size() - 5, buffer.size()).clear();
                listener.beginFormat(Format.STRIKEDOUT, Listener.EMPTY_PARAMETERS);
            }
            if (helper.getStringRepresentation(buffer).endsWith("</del>")) {
                //generate strikeout open event
                this.paragraphJustOpened = helper.processWords(6, buffer, listener, this.paragraphJustOpened);
                listener.endFormat(Format.STRIKEDOUT, Listener.EMPTY_PARAMETERS);
            }
        }
        //parse remaining as strings
        if (buffer.size() > 0) {
            this.paragraphJustOpened = helper.processWords(0, buffer, listener, this.paragraphJustOpened);
        }
        //close remaining list items
        while (!listStack.isEmpty()) {
            listener.endListItem();
            closeList(listener, listStack);
        }

        //close remaining quotations
        while (quotationIndentation >= 0) {
            listener.endQuotationLine();
            listener.endQuotation(Listener.EMPTY_PARAMETERS);
            quotationIndentation--;
        }
        if (inParagraph) {
            listener.endParagraph(Listener.EMPTY_PARAMETERS);
            inParagraph = false;
        }
    }

    private void processWordsFromReader(char ch, Reader source, Listener listener, String endString) throws IOException
    {
        ArrayList<Character> buffer = new ArrayList<>();
        buffer.add(ch);
        int c = source.read();
        while (source.ready() && c != -1) {
            buffer.add((char) c);
            String readString = helper.getStringRepresentation(buffer);
            if (readString.endsWith(endString)) {
                buffer.subList(buffer.size() - endString.length(), buffer.size()).clear();
                //StringBuilder here has no utility.
                this.paragraphJustOpened = helper.processWords(0, buffer, listener, this.paragraphJustOpened);
                break;
            }
            c = source.read();
        }
    }

    private void processImage(
            ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException
    {
        String imageArgument = helper.getStringRepresentation(buffer);
        boolean internalImage = true;
        Map<String, String> param = new HashMap<>();
        if (imageArgument.startsWith(TAG_RSS_GREATER_THAN_SYMBOL)) {
            return;
        }

        if (imageArgument.endsWith("}}")) {
            String imageName;
            if (!imageArgument.contains("wiki:")) {
                internalImage = false;
            }
            if (imageArgument.startsWith(" ")
                    && imageArgument.endsWith(TAG_SPACE_DOUBLE_CLOSING_CURLY_BRACKETS))
            {
                //align centre
                param.put(TAG_ALIGN, "middle");
                imageArgument = imageArgument.substring(1);
            } else if (imageArgument.startsWith(" ")) {
                //align left
                param.put(TAG_ALIGN, TAG_LEFT);
                imageArgument = imageArgument.substring(1);
            } else if (imageArgument.endsWith(TAG_SPACE_DOUBLE_CLOSING_CURLY_BRACKETS)) {
                //align right
                param.put(TAG_ALIGN, TAG_RIGHT);
            }
            if (internalImage) {
                imageName = imageArgument.substring(5, imageArgument.length() - 2);
            } else {
                imageName = imageArgument.substring(1, imageArgument.length() - 2);
            }
            imageName = imageName.trim();
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
            ResourceReference reference = new ResourceReference(imageName, ResourceType.ATTACHMENT);
            reference.setTyped(false);
            listener.onImage(reference, false, param);
            buffer.clear();
            return;
        }
    }

    private void processLink(Reader source, Listener listener) throws IOException
    {
        int c;
        ArrayList<Character> functionBuffer = new ArrayList<>();
        c = source.read();
        while (source.ready() && c != -1) {
            functionBuffer.add((char) c);
            String bufferString = helper.getStringRepresentation(functionBuffer);
            if (bufferString.endsWith("doku>") || bufferString.endsWith("wp>") || bufferString.endsWith("phpfn>") ||
                    bufferString.endsWith("google>") || bufferString.endsWith("skype>"))
            {
                InterWikiResourceReference reference;
                functionBuffer.remove(functionBuffer.size() - 1);
                functionBuffer.add(':');
                ArrayList<Character> Buffer = new ArrayList<>();
                while (c != ']') {
                    c = source.read();
                    Buffer.add((char) c);
                }
                source.read();
                Buffer.remove(Buffer.size() - 1);
                reference = new InterWikiResourceReference(helper.getStringRepresentation(Buffer));

                if (helper.getStringRepresentation(functionBuffer).startsWith("doku")) {
                    reference.setInterWikiAlias("doku");
                } else if (helper.getStringRepresentation(functionBuffer).startsWith("wp")) {

                    reference.setInterWikiAlias("wp");
                } else if (helper.getStringRepresentation(functionBuffer).startsWith("phpfn")) {
                    reference.setInterWikiAlias("phpfn");
                } else if (helper.getStringRepresentation(functionBuffer).startsWith("google")) {
                    reference.setInterWikiAlias("google");
                } else if (helper.getStringRepresentation(functionBuffer).startsWith("skype")) {
                    reference.setInterWikiAlias("skype");
                } else {
                    continue;
                }

                reference.setTyped(true);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }

            ResourceReference reference;
            if ((char) c == '|') {
                c = (char) source.read();
                if (functionBuffer.get(0) == '<' && functionBuffer.get(functionBuffer.size() - 2) == '>'
                        && functionBuffer.contains('@'))
                {
                    //process mailto
                    reference = new ResourceReference(helper.getStringRepresentation(
                            new ArrayList<>(functionBuffer
                                    .subList(1, functionBuffer.size() - 2))), ResourceType.MAILTO);
                    reference.setTyped(true);
                    listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                    processWordsFromReader((char) c, source, listener, TAG_DOUBLE_CLOSING_SQUARE_BRACKETS);
                    listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                    break;
                } else {
                    if (c != '{') {
                        reference = new ResourceReference(helper.getStringRepresentation(
                                new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() - 1))),
                                ResourceType.URL);
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

            if (helper.getStringRepresentation(functionBuffer).endsWith("{{")) {
                reference = new ResourceReference(
                        helper.getStringRepresentation(
                                new ArrayList<>(functionBuffer.subList(0, functionBuffer.size() - 3))),
                        ResourceType.URL);
                reference.setTyped(false);
                listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
                functionBuffer.clear();
                functionBuffer = helper.readIntoBuffer(functionBuffer, source);
                processImage(functionBuffer, source, listener);
                source.skip(2);
                listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
                break;
            }

            if (helper.getStringRepresentation(functionBuffer).endsWith(TAG_DOUBLE_CLOSING_SQUARE_BRACKETS)) {
                reference = new ResourceReference(helper.getStringRepresentation(
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
            this.paragraphJustOpened = helper.processWords(0, buffer, listener, this.paragraphJustOpened);
            listener.endTableCell(param);
        }
        if (inHeadCell) {
            trimBuffer(buffer);
            listener.beginTableHeadCell(param);
            this.paragraphJustOpened = helper.processWords(0, buffer, listener, this.paragraphJustOpened);
            listener.endTableHeadCell(param);
        }
    }

    private void processEmailAddressFromBuffer(ArrayList<Character> buffer, Listener listener)
    {
        buffer.remove(0);
        buffer.remove(buffer.size() - 1);
        ResourceReference reference =
                new ResourceReference(helper.getStringRepresentation(buffer), ResourceType.MAILTO);
        listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
        listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
        buffer.clear();
    }

    private void closeList(Listener listener, Deque<ListState> listType)
    {
        if (listType.pop().getType() == ListType.BULLETED) {
            listener.endList(ListType.BULLETED, Listener.EMPTY_PARAMETERS);
        } else {
            listener.endList(ListType.NUMBERED, Listener.EMPTY_PARAMETERS);
        }
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