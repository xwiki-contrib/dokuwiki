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
package org.xwiki.contrib.dokuwiki.text.internal.input;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;

/**
 * DokuWiki deaccent helper component.
 *
 * @version $Id$
 */
@Component(roles = DokuWikiDeaccent.class)
@Singleton
public class DokuWikiDeaccent
{
    private static final String A = "a";

    private static final String AE = "ae";

    private static final String C = "c";

    private static final String D = "d";

    private static final String E = "e";

    private static final String F = "f";

    private static final String G = "g";

    private static final String H = "h";

    private static final String I = "i";

    private static final String L = "l";

    private static final String N = "n";

    private static final String O = "o";

    private static final String R = "r";

    private static final String S = "s";

    private static final String T = "t";

    private static final String U = "u";

    private static final String W = "w";

    private static final String Y = "y";

    private static final String Z = "z";

    // Strings copied from
    // https://github.com/dokuwiki/dokuwiki/blob/a178f5e035771d/inc/Utf8/tables/loweraccents.php
    // for compatibility with DokuWiki.
    private final String[] searches = new String[]{"á", "à", "ă", "â", "å", "ä", "ã", "ą", "ā", "æ", "ḃ", "ć", "ĉ",
        "č", "ċ", "ç", "ď", "ḋ", "đ", "ð", "é", "è", "ĕ", "ê", "ě", "ë", "ė", "ę", "ē", "ḟ", "ƒ", "ğ", "ĝ", "ġ", "ģ",
        "ĥ", "ħ", "í", "ì", "î", "ï", "ĩ", "į", "ī", "ı", "ĵ", "ķ", "ĺ", "ľ", "ļ", "ł", "ṁ", "ń", "ň", "ñ", "ņ", "ó",
        "ò", "ô", "ö", "ő", "õ", "ø", "ō", "ơ", "ṗ", "ŕ", "ř", "ŗ", "ś", "ŝ", "š", "ṡ", "ş", "ș", "ß", "ť", "ṫ", "ţ",
        "ț", "ŧ", "ú", "ù", "ŭ", "û", "ů", "ü", "ű", "ũ", "ų", "ū", "ư", "ẃ", "ẁ", "ŵ", "ẅ", "ý", "ỳ", "ŷ", "ÿ", "ź",
        "ž", "ż", "þ", "µ"};

    private final String[] replacements = new String[]{ A, A, A, A, A, AE, A, A, A, AE, "b", C, C, C, C, C, D, D, D,
        "dh", E, E, E, E, E, E, E, E, E, F, F, G, G, G, G, H, H, I, I, I, I, I, I, I, I, "j", "k", L, L, L, L, "m", N,
        N, N, N, O, O, O, "oe", O, O, O, O, O, "p", R, R, R, S, S, S, S, S, S, "ss", T, T, T, T, T, U, U, U, U, U, "ue",
        U, U, U, U, U, W, W, W, W, Y, Y, Y, Y, Z, Z, Z, "th", U };

    /**
     * Deaccent a string.
     *
     * @param string the string to deaccent
     * @return the deaccented string
     */
    public String deaccent(String string)
    {
        return StringUtils.replaceEach(string, searches, replacements);
    }
}
