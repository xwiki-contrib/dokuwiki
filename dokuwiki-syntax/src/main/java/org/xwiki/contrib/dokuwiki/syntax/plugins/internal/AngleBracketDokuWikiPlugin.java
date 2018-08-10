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
package org.xwiki.contrib.dokuwiki.syntax.plugins.internal;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxParserHelper;
import org.xwiki.contrib.dokuwiki.syntax.plugins.DokuWikiAngleBracketPlugin;
import org.xwiki.contrib.dokuwiki.syntax.plugins.DokuWikiPlugin;
import org.xwiki.rendering.listener.Listener;

/**
 * DokuWiki Angle plugin parser.
 *
 * @version $Id: $
 * @since 1.3
 */
@Component
@Named("angleBracket")
@Singleton
public class AngleBracketDokuWikiPlugin implements DokuWikiPlugin
{
    @Inject
    private Logger logger;

    @Inject
    private DokuWikiSyntaxParserHelper helper;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Override
    public void parse(ArrayList<Character> buffer, Reader source, Listener listener)
            throws IOException
    {
        List<DokuWikiAngleBracketPlugin> componentList = null;

        // TODO : add support
        try {
            componentList = componentManagerProvider.get().getInstanceList(DokuWikiAngleBracketPlugin.class);
        } catch (ComponentLookupException e) {
            this.logger.error("Failed to get the ComponentList", e);
        }

        if (componentList != null) {
            for (DokuWikiAngleBracketPlugin plugin : componentList) {
                plugin.parse(buffer, source, listener);
            }
        } else {
            this.logger.error("Failed to get ComponentManager instance");
        }
    }
}
