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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.listener.chaining.ChainingListener;
import org.xwiki.rendering.listener.chaining.EventType;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.chaining.LookaheadChainingListener;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.util.IdGenerator;

/**
 * Listener for the Antlr parser.
 *
 * @version $Id$
 * @since 2.0
 */
@Component(roles = DokuWikiParserVisitor.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiParserVisitor extends DokuWikiGrammarBaseVisitor<Object>
{
    private static final String CODE_MACRO = "code";

    private static final Pattern HEADING_MARKER_PATTERN = Pattern.compile("^=+");

    private static final String LINE_BREAK = "\n";

    private static final String ALIGN_PARAMETER = "align";

    private static final String CLOSING_ANGLE = ">";

    private static final String LANGUAGE_PARAMETER = "language";

    /**
     * Helper class for wrapping a non-chaining listener as chaining listener to be used at the end of a listener chain.
     */
    private static class ChainingWrappingListener extends WrappingListener implements ChainingListener
    {
        protected ChainingWrappingListener(Listener listener)
        {
            setWrappedListener(listener);
        }

        @Override
        public ListenerChain getListenerChain()
        {
            return null;
        }
    }

    private static class ListState
    {
        private final int depth;

        private final ListType type;

        ListState(int depth, ListType type)
        {
            this.depth = depth;
            this.type = type;
        }

        int getDepth()
        {
            return this.depth;
        }

        ListType getType()
        {
            return this.type;
        }
    }

    private enum BlockState
    {
        NONE,
        PARAGRAPH,
        LIST,
        TABLE,
        QUOTE
    }

    private LookaheadChainingListener listener;

    private MetaData metaData;

    @Inject
    @Named("link")
    private SingleDokuWikiSyntaxParser linkSyntaxParser;

    @Inject
    @Named("image")
    private SingleDokuWikiSyntaxParser imageSyntaxParser;

    @Inject
    @Named("inline/plain")
    private SingleDokuWikiSyntaxParser plainParser;

    private final IdGenerator idGenerator = new IdGenerator();

    private final Deque<Format> formattingStack = new ArrayDeque<>();

    private int quoteDepth;

    private int sectionLevel;

    private BlockState blockState = BlockState.NONE;

    private final Deque<ListState> listStack = new ArrayDeque<>();

    /**
     * @param listener the listener to generate events on
     */
    public void setListener(Listener listener)
    {
        // Wrap the listener in a LookaheadChainingListener so that we can look ahead in the stream to detect the
        // last event that was sent. This is needed to detect when to create a space.
        ListenerChain chain = new ListenerChain();
        this.listener = new LookaheadChainingListener(chain, 1);
        chain.addListener(this.listener);
        chain.addListener(new ChainingWrappingListener(listener));
    }

    /**
     * @param metaData the metadata to set on the document
     */
    public void setMetaData(MetaData metaData)
    {
        this.metaData = metaData;
    }

    @Override
    public Object visitDocument(DokuWikiGrammarParser.DocumentContext ctx)
    {
        this.listener.beginDocument(this.metaData);
        super.visitDocument(ctx);
        closeCurrentBlock();

        while (this.sectionLevel > 0) {
            this.listener.endSection(Listener.EMPTY_PARAMETERS);
            this.sectionLevel--;
        }

        this.listener.endDocument(this.metaData);
        return null;
    }

    @Override
    public Object visitCode(DokuWikiGrammarParser.CodeContext ctx)
    {
        String text = ctx.getText();

        String tagName = ctx.CODE() != null ? CODE_MACRO : "file";

        // Strip <code [^>]*> and </code> tags
        String content = StringUtils.removeEnd(StringUtils.substringAfter(text, CLOSING_ANGLE),
            "</" + tagName + CLOSING_ANGLE);

        if (content.startsWith(LINE_BREAK)) {
            content = content.substring(1);
        }

        if (content.endsWith(LINE_BREAK)) {
            content = content.substring(0, content.length() - 1);
        }

        Map<String, String> parametersMap = new HashMap<>();
        String parameterString = StringUtils.trim(StringUtils.substringBetween(text, "<" + tagName, CLOSING_ANGLE));

        String[] parameters = StringUtils.split(parameterString);

        if (parameters != null && parameters.length > 0) {
            String language = parameters[0];

            if (!language.equals("-") && !language.startsWith("[")) {
                parametersMap.put(LANGUAGE_PARAMETER, language);
            }
        }

        ensureBlockAcceptable();
        this.listener.onMacro(CODE_MACRO, parametersMap, content, false);
        return null;
    }

    @Override
    public Object visitLink(DokuWikiGrammarParser.LinkContext ctx)
    {
        ensureInline();
        String text = ctx.getText();
        this.linkSyntaxParser.parse(text.substring(2, text.length() - 2), this.listener);
        return null;
    }

    @Override
    public Object visitFreeStandingUrl(DokuWikiGrammarParser.FreeStandingUrlContext ctx)
    {
        ensureInline();
        String text = ctx.getText();

        // If the URL is not prefixed with a protocol, assume it's http as that's what DokuWiki does. XWiki doesn't
        // support freestanding URLs without protocol.
        if (ctx.FREESTANDING_WWW() != null) {
            text = "http://" + text;
        }
        ResourceReference reference = new ResourceReference(text, ResourceType.URL);
        reference.setTyped(false);
        this.listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
        this.listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
        return null;
    }

    @Override
    public Object visitMedia(DokuWikiGrammarParser.MediaContext ctx)
    {
        ensureInline();
        String text = ctx.getText();
        this.imageSyntaxParser.parse(text.substring(2, text.length() - 2), this.listener);
        return null;
    }

    @Override
    public Object visitBold(DokuWikiGrammarParser.BoldContext ctx)
    {
        toggleFormat(Format.BOLD);

        return null;
    }

    @Override
    public Object visitItalic(DokuWikiGrammarParser.ItalicContext ctx)
    {
        toggleFormat(Format.ITALIC);

        return null;
    }

    @Override
    public Object visitMonospace(DokuWikiGrammarParser.MonospaceContext ctx)
    {
        toggleFormat(Format.MONOSPACE);

        return null;
    }

    @Override
    public Object visitUnderline(DokuWikiGrammarParser.UnderlineContext ctx)
    {
        toggleFormat(Format.UNDERLINED);

        return null;
    }

    @Override
    public Object visitDelStart(DokuWikiGrammarParser.DelStartContext ctx)
    {
        maybeBeginFormat(Format.STRIKEDOUT, ctx.getText());

        return null;
    }

    @Override
    public Object visitDelEnd(DokuWikiGrammarParser.DelEndContext ctx)
    {
        maybeEndFormat(Format.STRIKEDOUT, ctx.getText());

        return null;
    }

    @Override
    public Object visitSubStart(DokuWikiGrammarParser.SubStartContext ctx)
    {
        maybeBeginFormat(Format.SUBSCRIPT, ctx.getText());

        return null;
    }

    @Override
    public Object visitSubEnd(DokuWikiGrammarParser.SubEndContext ctx)
    {
        maybeEndFormat(Format.SUBSCRIPT, ctx.getText());

        return null;
    }

    @Override
    public Object visitSupStart(DokuWikiGrammarParser.SupStartContext ctx)
    {
        maybeBeginFormat(Format.SUPERSCRIPT, ctx.getText());

        return null;
    }

    @Override
    public Object visitSupEnd(DokuWikiGrammarParser.SupEndContext ctx)
    {
        maybeEndFormat(Format.SUPERSCRIPT, ctx.getText());

        return null;
    }

    private void maybeBeginFormat(Format format, String alternativeText)
    {
        if (!this.formattingStack.contains(format)) {
            beginFormat(format);
        } else {
            this.plainParser.parse(alternativeText, this.listener);
        }
    }

    private void maybeEndFormat(Format format, String alternativeText)
    {
        if (this.formattingStack.contains(format)) {
            endFormat(format);
        } else {
            this.plainParser.parse(alternativeText, this.listener);
        }
    }

    private void toggleFormat(Format format)
    {
        ensureInline();

        if (this.formattingStack.contains(format)) {
            endFormat(format);
        } else {
            beginFormat(format);
        }
    }

    private void beginFormat(Format format)
    {
        ensureInline();

        this.listener.beginFormat(format, Listener.EMPTY_PARAMETERS);
        this.formattingStack.push(format);
    }

    private void endFormat(Format format)
    {
        Deque<Format> tempStack = new ArrayDeque<>();
        while (!this.formattingStack.isEmpty() && !this.formattingStack.peek().equals(format)) {
            tempStack.push(this.formattingStack.pop());
        }

        this.listener.endFormat(format, Listener.EMPTY_PARAMETERS);
        this.formattingStack.pop();
        while (!tempStack.isEmpty()) {
            beginFormat(tempStack.pop());
        }
    }

    @Override
    public Object visitHtml(DokuWikiGrammarParser.HtmlContext ctx)
    {
        // Strip <html> and </html> tags
        String content = StringUtils.removeEndIgnoreCase(
            StringUtils.removeStartIgnoreCase(ctx.getText(), "<html>"),
            "</html>");

        TerminalNode inlineHTML = ctx.getToken(DokuWikiGrammarLexer.HTML, 0);

        boolean isInline = inlineHTML != null;

        if (isInline) {
            ensureInline();
        } else {
            ensureBlockAcceptable();
        }

        this.listener.onMacro("html", Listener.EMPTY_PARAMETERS, content, inlineHTML != null);
        return null;
    }

    @Override
    public Object visitPhp(DokuWikiGrammarParser.PhpContext ctx)
    {
        // Strip <php> and </php> tags
        String content = StringUtils.removeEndIgnoreCase(
            StringUtils.removeStartIgnoreCase(ctx.getText(), "<php>"),
            "</php>");

        boolean isInline = ctx.getToken(DokuWikiGrammarLexer.PHP, 0) != null;

        if (isInline) {
            ensureInline();
        } else {
            ensureBlockAcceptable();
        }

        this.listener.onMacro(CODE_MACRO, Collections.singletonMap(LANGUAGE_PARAMETER, "php"), content, isInline);
        return null;
    }

    @Override
    public Object visitListItem(DokuWikiGrammarParser.ListItemContext ctx)
    {
        if (this.blockState != BlockState.LIST) {
            closeCurrentBlock();
        }

        String listIndent = StringUtils.stripEnd(ctx.LIST_INDENT().getText().substring(1), " ");

        ListType listType = listIndent.charAt(listIndent.length() - 1) == '*' ? ListType.BULLETED : ListType.NUMBERED;

        String indentation = listIndent.substring(0, listIndent.length() - 1).replace("\t", "  ");
        int listDepth = indentation.length() / 2;

        if (this.listStack.isEmpty()) {
            this.listener.beginList(listType, Listener.EMPTY_PARAMETERS);
            this.listStack.push(new ListState(listDepth, listType));
        } else {
            if (listDepth < this.listStack.getLast().getDepth()) {
                listDepth = this.listStack.getLast().getDepth();
            }

            if (listDepth > this.listStack.peek().getDepth()) {
                this.listener.beginList(listType, Listener.EMPTY_PARAMETERS);
                this.listStack.push(new ListState(listDepth, listType));
            } else {
                while (!this.listStack.isEmpty() && listDepth < this.listStack.peek().getDepth()) {
                    this.listener.endListItem();
                    closeList();
                }

                this.listener.endListItem();
                ensureListType(listType, listDepth);
            }
        }

        this.listener.beginListItem();
        this.blockState = BlockState.LIST;

        ctx.contentWithSyntax().accept(this);

        closeFormatting();
        return null;
    }

    private void ensureListType(ListType listType, int listDepth)
    {
        if (this.listStack.isEmpty() || listType != this.listStack.peek().getType()) {
            closeList();
            this.listener.beginList(listType, Listener.EMPTY_PARAMETERS);
            this.listStack.push(new ListState(listDepth, listType));
        }
    }

    private void closeList()
    {
        if (!this.listStack.isEmpty()) {
            this.listener.endList(this.listStack.pop().getType(), Listener.EMPTY_PARAMETERS);
        }
    }


    @Override
    public Object visitQuote(DokuWikiGrammarParser.QuoteContext ctx)
    {
        if (this.blockState != BlockState.QUOTE) {
            closeCurrentBlock();
            this.blockState = BlockState.QUOTE;
        }

        int newQuoteDepth = ctx.QUOTE_START().getText().trim().length();

        if (this.quoteDepth >= newQuoteDepth) {
            for (; this.quoteDepth > newQuoteDepth; this.quoteDepth--) {
                this.listener.endQuotationLine();
                this.listener.endQuotation(Listener.EMPTY_PARAMETERS);
            }
            this.listener.endQuotationLine();
            this.listener.beginQuotationLine();
        } else {
            for (; this.quoteDepth < newQuoteDepth; this.quoteDepth++) {
                this.listener.beginQuotation(Listener.EMPTY_PARAMETERS);
                this.listener.beginQuotationLine();
            }
        }

        ctx.contentWithSyntax().accept(this);
        closeFormatting();

        return null;
    }

    @Override
    public Object visitParagraph(DokuWikiGrammarParser.ParagraphContext ctx)
    {
        // Close the previous block when it isn't a paragraph. Close the previous paragraph if the paragraph consists
        // only of whitespace.
        if (StringUtils.isBlank(ctx.getText()) || this.blockState != BlockState.PARAGRAPH) {
            closeCurrentBlock();
        }

        // If the previous line was also a paragraph, insert a space so simple line breaks get converted to spaces.
        // However, don't do this if there is already a space or a newline syntax at the end of the previous line.
        if (this.blockState == BlockState.PARAGRAPH && !startsWithBlock(ctx.contentWithSyntax())
            && this.listener.getNextEvent().eventType != EventType.ON_SPACE
            && this.listener.getNextEvent().eventType != EventType.ON_NEW_LINE) {
            this.listener.onSpace();
        }

        // Ignore the newline at the end of the paragraph
        ctx.contentWithSyntax().accept(this);

        return null;
    }

    @Override
    public Object visitRss(DokuWikiGrammarParser.RssContext ctx)
    {
        ensureBlockAcceptable();

        Map<String, String> param = new HashMap<>();
        String[] arguments = StringUtils.split(ctx.getText());
        param.put("feed", arguments[0].substring(6));

        // Get the count - the first number we find
        String count = Arrays.stream(arguments).skip(1)
            .filter(StringUtils::isAsciiPrintable)
            .filter(StringUtils::isNumeric).findFirst().orElse("8");
        param.put("count", count);
        if (Arrays.asList(arguments).contains("description")) {
            param.put("content", "true");
        }
        this.listener.onMacro("rss", param, null, false);

        return null;
    }

    @Override
    public Object visitTable(DokuWikiGrammarParser.TableContext ctx)
    {
        closeCurrentBlock();
        this.listener.beginTable(Listener.EMPTY_PARAMETERS);
        this.blockState = BlockState.TABLE;

        ctx.tableRow().forEach(this::visitTableRow);

        this.listener.endTable(Listener.EMPTY_PARAMETERS);
        this.blockState = BlockState.NONE;

        return null;
    }

    @Override
    public Object visitTableRow(DokuWikiGrammarParser.TableRowContext ctx)
    {
        this.listener.beginTableRow(Listener.EMPTY_PARAMETERS);

        List<DokuWikiGrammarParser.TableCellContext> cells = ctx.tableCell();

        // Skip empty cells at the beginning of the row as these are also skipped by DokuWiki.
        int skipStart = 0;
        while (skipStart < cells.size() && cells.get(skipStart).getText().length() == 1) {
            skipStart++;
        }
        // Skip the last "cell" as it should only be the end marker of the table row.
        cells.stream().limit(cells.size() - 1L).skip(skipStart).forEach(this::visitTableCell);

        this.listener.endTableRow(Listener.EMPTY_PARAMETERS);

        return null;
    }

    @Override
    public Object visitTableCell(DokuWikiGrammarParser.TableCellContext ctx)
    {
        boolean startPadding = ctx.tableCellStartPadding().getText().length() > 1;
        boolean endPadding = ctx.tableCellEndPadding().getText().length() > 1;
        boolean isHeader = ctx.CARET() != null;
        boolean isEmpty = ctx.tableCellContent().getText().isEmpty();

        Map<String, String> parameters;

        if (startPadding && endPadding) {
            parameters = Collections.singletonMap(ALIGN_PARAMETER, "center");
        } else if (startPadding && !isEmpty) {
            parameters = Collections.singletonMap(ALIGN_PARAMETER, "right");
        } else {
            parameters = Collections.emptyMap();
        }

        if (isHeader) {
            this.listener.beginTableHeadCell(parameters);
        } else {
            this.listener.beginTableCell(parameters);
        }

        ctx.tableCellContent().accept(this);

        closeFormatting();

        if (isHeader) {
            this.listener.endTableHeadCell(parameters);
        } else {
            this.listener.endTableCell(parameters);
        }

        return null;
    }

    @Override
    public Object visitHorizontalRule(DokuWikiGrammarParser.HorizontalRuleContext ctx)
    {
        ensureBlockAcceptable();
        this.listener.onHorizontalLine(Listener.EMPTY_PARAMETERS);
        return null;
    }

    private boolean startsWithBlock(DokuWikiGrammarParser.ContentWithSyntaxContext ctx)
    {
        ParseTree firstChild = ctx.getChild(0);
        boolean isBlock = firstChild instanceof DokuWikiGrammarParser.CodeContext;
        if (!isBlock && firstChild instanceof DokuWikiGrammarParser.HtmlContext) {
            DokuWikiGrammarParser.HtmlContext htmlContext = (DokuWikiGrammarParser.HtmlContext) firstChild;
            isBlock = htmlContext.getToken(DokuWikiGrammarLexer.BLOCK_HTML, 0) != null;
        } else if (!isBlock && firstChild instanceof DokuWikiGrammarParser.PhpContext) {
            DokuWikiGrammarParser.PhpContext phpContext = (DokuWikiGrammarParser.PhpContext) firstChild;
            isBlock = phpContext.getToken(DokuWikiGrammarLexer.BLOCK_PHP, 0) != null;
        }
        return isBlock;
    }

    @Override
    public Object visitHeading(DokuWikiGrammarParser.HeadingContext ctx)
    {
        // Headings are always top-level elements in DokuWiki
        closeCurrentBlock();

        String heading = ctx.getText().trim();

        // Strip the heading delimiter
        Matcher headingMarkerMatcher = HEADING_MARKER_PATTERN.matcher(heading);
        if (headingMarkerMatcher.find()) {
            String headingMarker = headingMarkerMatcher.group();
            heading = StringUtils.strip(heading, "= \t");

            int numCharacters = headingMarker.length();
            int level = 7 - numCharacters;
            if (level > 6) {
                level = 6;
            }
            if (level < 1) {
                level = 1;
            }

            String id = this.idGenerator.generateUniqueId("H", heading);
            HeaderLevel headerLevel = HeaderLevel.parseInt(level);
            // Close any open sections that are deeper or equal to the current level
            while (this.sectionLevel >= level) {
                this.listener.endSection(Listener.EMPTY_PARAMETERS);
                this.sectionLevel--;
            }
            // Open sections until we reach the current level. If we stay in the same level, this will open a new
            // section as the previous loop closed it.
            while (this.sectionLevel < level) {
                this.sectionLevel++;
                this.listener.beginSection(Listener.EMPTY_PARAMETERS);
            }
            this.listener.beginHeader(headerLevel, id, Listener.EMPTY_PARAMETERS);
            this.plainParser.parse(heading, this.listener);
            this.listener.endHeader(headerLevel, id, Listener.EMPTY_PARAMETERS);
        }
        return null;
    }

    @Override
    public Object visitPreformatted(DokuWikiGrammarParser.PreformattedContext ctx)
    {
        closeCurrentBlock();

        // Ignore empty preformatted blocks, just make sure that they start a new block/paragraph.
        if (StringUtils.isNotBlank(ctx.getText())) {
            String content = ctx.PREFORMATTED().stream()
                .map(ParseTree::getText)
                .map(line -> StringUtils.removeStart(line, "\n  "))
                .collect(Collectors.joining(LINE_BREAK));
            this.listener.onMacro(CODE_MACRO, Listener.EMPTY_PARAMETERS, content, false);
        }

        return null;
    }

    @Override
    public Object visitUnformatted(DokuWikiGrammarParser.UnformattedContext ctx)
    {
        String content;
        if (ctx.NOWIKI() != null) {
            // Strip <nowiki> and </nowiki> tags
            content = StringUtils.removeEnd(StringUtils.removeStart(ctx.getText(), "<nowiki>"), "</nowiki>");
        } else {
            // Strip '%%' tags
            content = ctx.getText().substring(2, ctx.getText().length() - 2);
        }

        this.listener.onVerbatim(content, this.blockState != BlockState.NONE, Listener.EMPTY_PARAMETERS);
        return null;
    }

    @Override
    public Object visitFootnote(DokuWikiGrammarParser.FootnoteContext ctx)
    {
        ensureInline();

        String footnote = ctx.getText().substring(2, ctx.getText().length() - 2);
        this.listener.onMacro("footnote", Listener.EMPTY_PARAMETERS, footnote, true);

        return null;
    }

    private void ensureBlockAcceptable()
    {
        if (this.blockState == BlockState.PARAGRAPH) {
            closeCurrentBlock();
        }
    }

    private void closeCurrentBlock()
    {
        closeFormatting();
        switch (this.blockState) {
            case QUOTE:
                while (this.quoteDepth > 0) {
                    this.listener.endQuotationLine();
                    this.listener.endQuotation(Listener.EMPTY_PARAMETERS);
                    this.quoteDepth--;
                }
                break;
            case LIST:
                while (!this.listStack.isEmpty()) {
                    this.listener.endListItem();
                    closeList();
                }
                break;
            case PARAGRAPH:
                this.listener.endParagraph(Listener.EMPTY_PARAMETERS);
                break;
            default:
                break;
        }
        this.blockState = BlockState.NONE;
    }

    private void closeFormatting()
    {
        while (!this.formattingStack.isEmpty()) {
            this.listener.endFormat(this.formattingStack.pop(), Listener.EMPTY_PARAMETERS);
        }
    }

    private void ensureInline()
    {
        if (this.blockState == BlockState.NONE) {
            this.listener.beginParagraph(Listener.EMPTY_PARAMETERS);
            this.blockState = BlockState.PARAGRAPH;
        }
    }

    @Override
    public Object visitTerminal(TerminalNode node)
    {
        switch (node.getSymbol().getType()) {
            case DokuWikiGrammarLexer.FREESTANDING_EMAIL:
                ensureInline();
                String email = node.getText();
                email = email.substring(1, email.length() - 1);
                ResourceReference reference = new ResourceReference(email, ResourceType.MAILTO);
                this.listener.beginLink(reference, true, Listener.EMPTY_PARAMETERS);
                this.listener.endLink(reference, true, Listener.EMPTY_PARAMETERS);
                break;
            case DokuWikiGrammarLexer.MANUAL_LINEBREAK:
                ensureInline();
                this.listener.onNewLine();
                break;
            case DokuWikiGrammarLexer.SPACE:
                ensureInline();
                // Prevent consecutive spaces as HTML renders them as one space while XWiki ensures that each space
                // is printed.
                if (this.listener.getNextEvent().eventType != EventType.ON_SPACE) {
                    this.listener.onSpace();
                }
                break;
            case Recognizer.EOF:
                // ignore.
                break;
            default:
                ensureInline();
                this.plainParser.parse(node.getText(), this.listener);
                break;
        }

        return null;
    }
}
