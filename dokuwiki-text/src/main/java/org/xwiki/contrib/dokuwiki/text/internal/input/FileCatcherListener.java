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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
//import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties;
import org.xwiki.filter.input.InputFilterStreamFactory;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;

/**
 * Find all files referenced in a wiki content.
 * 
 * @version $Id$
 */
@Component(roles = FileCatcherListener.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class FileCatcherListener extends WrappingListener
{
    @Inject
    //@Named(DokuWikiSyntaxInputProperties.FILTER_STREAM_TYPE_STRING)
    private InputFilterStreamFactory parserFactory;

    private DokuWikiInputFilterStream stream;

    private Set<String> files = new HashSet<>();

    void initialize(DokuWikiInputFilterStream stream)
    {
        this.stream = stream;
    }

    /**
     * @return the found referenced files
     */
    public Set<String> getFiles()
    {
        return this.files;
    }

    @Override
    public void beginLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        if (reference instanceof AttachmentResourceReference) {
            this.files.add(reference.getReference());
        }

        super.beginLink(reference, freestanding, parameters);
    }

    @Override
    public void onImage(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        if (reference instanceof AttachmentResourceReference) {
            this.files.add(reference.getReference());
        }

        super.onImage(reference, freestanding, parameters);
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        // Extract attachments from macro with wiki content
        // TODO: make it configurable
//        if (id.equals("gallery") || id.equals("blockquote")) {
//            DokuWikiSyntaxInputProperties parserProperties = stream.createDokuWikiSyntaxInputProperties(content);
//
//            // Generate events
//            try (BeanInputFilterStream<DokuWikiSyntaxInputProperties> contentStream =
//                ((BeanInputFilterStreamFactory) this.parserFactory).createInputFilterStream(parserProperties)) {
//                contentStream.read(this);
//            } catch (Exception e) {
//                // TODO log something ?
//            }
//        }

        super.onMacro(id, parameters, content, isInline);
    }
}
