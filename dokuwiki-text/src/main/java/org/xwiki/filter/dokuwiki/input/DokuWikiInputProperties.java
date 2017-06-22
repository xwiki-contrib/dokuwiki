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
package org.xwiki.filter.dokuwiki.input;

import org.xwiki.filter.type.FilterStreamType;

/**
 * DokuWiki XML input properties.
 * 
 * @version $Id: 408d9389abed98e4ab2fa6f528557e5d2c032b24 $
 * @deprecated since 1.8, use {@link org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties} instead
 */
@Deprecated
public class DokuWikiInputProperties extends org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties
{
    /**
     * The DokuWiki XML format.
     */
    public static final FilterStreamType FILTER_STREAM_TYPE =
        org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties.FILTER_STREAM_TYPE;

    /**
     * The DokuWiki XML format as String.
     */
    public static final String FILTER_STREAM_TYPE_STRING =
        org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties.FILTER_STREAM_TYPE_STRING;
}
