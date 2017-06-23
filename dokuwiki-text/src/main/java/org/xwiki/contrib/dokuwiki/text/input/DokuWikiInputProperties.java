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
package org.xwiki.contrib.dokuwiki.text.input;

import org.xwiki.filter.DefaultFilterStreamProperties;
import org.xwiki.filter.input.DefaultURLInputSource;
import org.xwiki.filter.input.InputSource;
import org.xwiki.filter.text.input.TextInputProperties;
import org.xwiki.filter.type.FilterStreamType;
import org.xwiki.filter.type.SystemType;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyName;

/**
 * DokuWiki TEXT input properties.
 * 
 * @version $Id: 408d9389abed98e4ab2fa6f528557e5d2c032b24 $
 */
public class DokuWikiInputProperties extends TextInputProperties {
    /**
     * The DokuWiki TEXT format.
     */
    // locally defined until updated in the framework
    private static final SystemType DOKUWIKI = new SystemType("dokuwiki");

    private static final String DATA_TEXT = "text";

    public static final FilterStreamType FILTER_STREAM_TYPE = new FilterStreamType(DOKUWIKI, DATA_TEXT);

    /**
     * The DokuWiki TEXT format as String.
     */
    public static final String FILTER_STREAM_TYPE_STRING = "dokuwiki+text";
}
