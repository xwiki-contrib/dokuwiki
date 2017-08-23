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
package org.xwiki.contrib.dokuwiki.text;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.runner.RunWith;
import org.xwiki.filter.test.integration.FilterTestSuite;
import org.xwiki.test.annotation.AllComponents;

/**
 * Run all tests found in the classpath. These {@code *.test} files must follow the conventions described in {@link
 * org.xwiki.filter.test.integration.TestDataParser}.
 *
 * @version $Id: 581bf6e732682b62158c05eb20237d6a13043d2d $
 */
@RunWith(FilterTestSuite.class)
@AllComponents
@FilterTestSuite.Scope(value = "dokuwikitext/"/*, pattern = "attached.test"*/)
public class IntegrationTests
{
    public IntegrationTests() throws URISyntaxException
    {
        // FIXME: remove when https://jira.xwiki.org/browse/XCOMMONS-1011 is fixed
        URL url = getClass().getResource("../../../../../");
        Path path = Paths.get(url.toURI());
        System.setProperty("xwiki.test.folder", path.toAbsolutePath().toString());
    }
}
