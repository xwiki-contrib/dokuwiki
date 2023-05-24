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

grammar DokuWikiGrammar;

@header {
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
}

/*
 * Parser rules.
 */
// Document token, the main entry point. Every alternative starts with consuming a newline and starts with a unique
// token. It is therefore important that each of them consumes everything until the next newline.
document
  : (heading | listItem | preformatted | quote | horizontalRule | table | paragraph)* EOF
  ;

// Heading. The token contains everything until the next newline.
heading
  : HEADING
  ;

// Preformatted content. The token contains everything until the next newline. Match all consecutive preformatted
// lines to make handling the match easier (they need to be grouped together).
preformatted
  : PREFORMATTED+
  ;

// Horizontal rule. The token contains everything until the next newline.
horizontalRule
  : HORIZONTAL_LINE
  ;

// List item. The contentWithSyntax should end at a newline.
listItem
  : LIST_INDENT contentWithSyntax
  ;

quote
  : QUOTE_START contentWithSyntax
  ;

paragraph
  : NEWLINE contentWithSyntax
  ;

// This needs to match every syntax that could possibly occur apart from those that start at the beginning of a line.
// The rules composed of several tokens (like bold) are listed before the tokens to avoid that the parser matches the
// individual token instead of the composed rule.
contentWithSyntax
  : (link|media|html|php|bold|italic|unformatted|code|underline|monospace|delStart|delEnd|subStart|subEnd|supStart
    |supEnd|footnote|SPACE|TAB|WORD|STAR|SPECIAL|SLASH|UNDERSCORE|SINGLE_QUOTE|MANUAL_LINEBREAK
    |FREESTANDING_EMAIL|freeStandingUrl|CARET|PIPE|COLON|rss)*
  ;

// Tables. Tables are composed of many different rules to make the parse tree more expressive and thus easier to
// process.
table: tableRow+;

tableRow
  : TABLE_START tableCell+
  ;

// The last table cell in a row should be empty and just consist of the caret or pipe. However, it is still important
// to match any content after it until the next newline.
tableCell: (CARET|PIPE) tableCellStartPadding tableCellContent tableCellEndPadding;

tableCellStartPadding: (SPACE|TAB)?;

tableCellEndPadding: (SPACE|TAB)?;

// This matches almost the same as contentWithSyntax with the difference that it excludes the table cell separators
// CARET and PIPE. Further, it matches non-greedily to ensure that the last space is matched by the tableCellEndPadding.
tableCellContent
  : (link|media|html|php|bold|italic|unformatted|code|underline|monospace|delStart|delEnd|subStart|subEnd|supStart
    |supEnd|footnote|SPACE|TAB|WORD|STAR|SPECIAL|SLASH|UNDERSCORE|SINGLE_QUOTE|MANUAL_LINEBREAK
    |FREESTANDING_EMAIL|freeStandingUrl|COLON|rss)*?
  ;

unformatted
  : NOWIKI | NOWIKI_ALT
  ;

freeStandingUrl
  : FREESTANDING_URL | FREESTANDING_WWW
  ;

delStart
  : DEL_START
  ;

delEnd
  : DEL_END
  ;

subStart
  : SUB_START
  ;

subEnd
  : SUB_END
  ;

supStart
  : SUP_START
  ;

supEnd
  : SUP_END
  ;

bold
  : STAR STAR
  ;

italic
  : SLASH SLASH
  ;

underline
  : UNDERSCORE UNDERSCORE
  ;

monospace
  : SINGLE_QUOTE SINGLE_QUOTE
  ;

html
  : HTML | BLOCK_HTML
  ;

php
  : PHP | BLOCK_PHP
  ;

code
  : CODE | FILE
  ;

link
  : LINK
  ;

media
  : MEDIA
  ;

rss
  : RSS
  ;

footnote
  : FOOTNOTE
  ;

/*
 * Lexer tokens.
 */
// Start with the "block" syntaxes. Each of them consumes a newline at the start to ensure that they don't match in
// the middle of a line. For parts where no wiki syntax is supported inside, the lexer matches the whole line.
LIST_INDENT: '\n ' ' '+ ('*' | '-') ' '*;
// ANTLR picks the longest match, so we need to make sure that preformatted doesn't match list items.
PREFORMATTED: '\n ' ' '+ (~[ *\-\n] ~[\n]*)?;
HEADING: '\n=' '='+ ~[\n]+ '=' '='+ [ \t]* {_input.LA(1) == '\n'}?;
QUOTE_START: '\n' '>'+ ' '*;
HORIZONTAL_LINE: '\n' [ \t]* '---' '-'+ [ \t]* {_input.LA(1) == '\n'}?;
// Don't match the actual table cell separator so we can nicely match it inside the table cell.
TABLE_START: '\n' { _input.LA(1) == '^' || _input.LA(1) == '|' }?;
// A single newline is used as start marker for a "paragraph" which might also be an empty line.
NEWLINE: '\n';

// Now inline syntaxes that match whole blocks of text.

// Don't match syntax inside footnotes as they are parsed separately in XWiki and this makes it easier to ensure that
// the parser is in the same state after the footnote as before (and not, e.g., in the middle of a table cell). This
// is not necessarily the same as in DokuWiki but the differences should be small (like DokuWiki won't recognize the
// closing footnote syntax when it is inside some other syntax). If necessary, we could enable parsing lexer tokens in
// footnotes and do the matching of end and start token in the parser, but this is most likely more expensive.
FOOTNOTE: '((' (~')' | ')' ~')')* '))';
NOWIKI_ALT: '%%' (~'%' | '%' ~'%')* '%%';
NOWIKI: '<nowiki>' (~'<' | '<' ~'/' | '</' ~'n' | '</n' ~'o' | '</no' ~'w' | '</now' ~'i' | '</nowi' ~'k' | '</nowik' ~'i' | '</nowiki' ~'>')* '</nowiki>';
CODE: '<code' ~'>'* '>' (~'<' | '<' ~'/' | '</' ~'c' | '</c' ~'o' | '</co' ~'d' | '</cod' ~'e' | '</code' ~'>')* '</code>';
FILE: '<file' ~'>'* '>' (~'<' | '<' ~'/' | '</' ~'f' | '</f' ~'i' | '</fi' ~'l' | '</fil' ~'e' | '</file' ~'>')* '</file>';
HTML: '<html>' (~'<' | '<' ~'/' | '</' ~'h' | '</h' ~'t' | '</ht' ~'m' | '</htm' ~'l' | '</html' ~'>')* '</html>';
BLOCK_HTML: '<HTML>' (~'<' | '<' ~'/' | '</' ~'H' | '</H' ~'T' | '</HT' ~'M' | '</HTM' ~'L' | '</HTML' ~'>')* '</HTML>';
PHP: '<php>' (~'<' | '<' ~'/' | '</' ~'p' | '</p' ~'h' | '</ph' | '</php' ~'>')* '</php>';
BLOCK_PHP: '<PHP>' (~'<' | '<' ~'/' | '</' ~'P' | '</P' ~'H' | '</PH' | '</PHP' ~'>')* '</PHP>';
// Match freestanding URLs and emails as much as possible as in DokuWiki. The lookahead has been converted to a
// regular expression. FTP URLs are omitted as FTP is largely obsolete.
fragment LTRS: [a-zA-Z0-9];
fragment PUNC: [.:?\-;,'];
fragment HOST: (LTRS | PUNC);
fragment ANY_NOT_PUNC: (LTRS | [/#~=&%@![\]]);
FREESTANDING_URL: [hH][tT][tT][pP] [sS]? '://' (ANY_NOT_PUNC | PUNC+ ANY_NOT_PUNC)+;
FREESTANDING_WWW: [wW][wW][wW] '.' HOST+? '.' HOST+? (ANY_NOT_PUNC | PUNC+ ANY_NOT_PUNC)+;
// Email addresses are matched as in DokuWiki with the exception that the length restriction on the last part have
// been removed as they are hard to express in ANTLR.
fragment RFC2822_ATEXT: [0-9a-zA-Z!#$%&'*+/=?^_`{|}~-];
FREESTANDING_EMAIL: '<' RFC2822_ATEXT+ ( '.' RFC2822_ATEXT+)* '@' ([0-9a-zA-Z][0-9a-zA-Z-]* '.')+ [a-zA-Z]+ '>';
LINK: '[[' (']' ~']' | ~']')+ ']' ']'+;
// RSS is basically a special case of media and could also be handled in the parser/the visitor which would have the
// advantage of being able to generically handle similar syntaxes introduced by plugins. However, for now it is easier
// to directly match it in the lexer.
RSS: '{{rss>' (~'}' | '}' ~'}')+ '}}';
MEDIA: '{{' (~'}' | '}' ~'}')* '}}';

// Explicit tokens for the start and end of some syntaxes. It might be possible to use the same token for all
// start/end syntaxes, this should be considered if plugins with similar syntaxes shall be supported but for now it
// is easier with explicitly defined tokens.
DEL_START: '<del>';
DEL_END: '</del>';
SUB_START: '<sub>';
SUB_END: '</sub>';
SUP_START: '<sup>';
SUP_END: '</sup>';

// Manual linebreak. It needs a space or tab after it, which is consumed, or a linebreak, which is not consumed.
MANUAL_LINEBREAK: '\\\\' ( [ \t] | {_input.LA(1) == '\n'}? );

// Match sequences of spaces as it makes it easier to collaps them in the parser (DokuWiki doesn't distinguish
// between a single and several spaces in text, only when they are used for indentation).
SPACE: ' '+;
TAB: '\t'+;

// Tokens that match single characters. There are individual tokens for characters that are used in the parser to
// identify syntaxes. While we might as well introduce special tokens for that, it seemed easier this way.
STAR: '*';
SLASH: '/';
UNDERSCORE: '_';
SINGLE_QUOTE: '\'';
CARET: '^';
PIPE: '|';
COLON: ':';

// Generic token for all kinds of special characters. It is important to match them as single characters and not in
// the catchall word below as otherwise they are swallowed by a word and not by the special syntax token (like a word
// ending in < instead of ending before < such that < can be recognized as part of <del>).
SPECIAL: '[' | ']' | '=' | '<' | '"' | '\\' | '>' | '(' | ')' | '%' | '-';
// Catchall token for all characters not yet matched. It is important to explicitly exclude those characters that
// need to be matched individually. Apart from that, this matches as much as possible to reduce the number of tokens
// to speed up parsing. The split in individual words happens later in the parser.
WORD: ~[ \t\n*[\]=/%"<>\\\-_'^|:()]+;