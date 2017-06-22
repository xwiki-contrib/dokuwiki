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

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.manager.ComponentManager;
//import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties;
//import org.xwiki.contrib.dokuwiki.syntax.internal.input.DokuWikiContentFilter;
//import org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.input.InputFilterStreamFactory;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.VoidListener;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.RenderingContext;

/**
 * Modify on the fly various events (link reference, macros, etc).
 * 
 * @version $Id$
 */
@Component(roles = DokuWikiContextConverterListener.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiContextConverterListener extends WrappingListener
{
    @Inject
    private FileCatcherListener fileCatcher;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
//    @Named(DokuWikiSyntaxInputProperties.FILTER_STREAM_TYPE_STRING)
    private InputFilterStreamFactory parserFactory;

    @Inject
    private RenderingContext renderingContext;

    @Inject
    private ComponentManager componentManager;

    private DokuWikiInputFilterStream stream;

    private Deque<ResourceReference> currentReference = new LinkedList<>();

    private Syntax targetSyntax;

    private Set<String> categories = new LinkedHashSet<>();

    void initialize(Listener listener, DokuWikiInputFilterStream stream, Syntax targetSyntax)
    {
//        setWrappedListener(listener);
//
//        this.fileCatcher.initialize(stream);
//        this.fileCatcher.setWrappedListener(new VoidListener());
//        this.stream = stream;
//        this.targetSyntax = targetSyntax;
    }

    /**
     * @return the catched files
     */
//    public Set<String> getFiles()
//    {
////        return this.fileCatcher.getFiles();
//    }

    public Set<String> getCategories()
    {
        return this.categories;
    }

    private EntityReference compact(EntityReference linkReference, EntityReference pageReference)
    {
        if (this.stream.getProperties().isAbsoluteReferences()) {
            return linkReference;
        }

        int diff = linkReference.size() - pageReference.size();

        if (diff < 0) {
            return linkReference;
        }

        EntityReference linkElement = linkReference;
        for (; diff > 0; --diff) {
            linkElement = linkElement.getParent();
        }

        // Child or same page
        if (linkElement.equals(pageReference)) {
            return linkReference.replaceParent(linkElement, null);
        }

        // Same parent
        if (linkElement.getParent() != null && pageReference.getParent() != null
            && linkElement.getParent().equals(pageReference.getParent())) {
            return linkReference.replaceParent(linkElement.getParent(), null);
        }

        return linkReference;
    }

    private String compact(EntityReference entityReference)
    {
        EntityReference compactReference;

        try {
            compactReference = compact(entityReference, this.stream.currentPageReference);
        } catch (Exception e) {
            // Bulletproofing
            compactReference = entityReference;
        }

        if (entityReference != compactReference && compactReference.size() > 1) {
            return "." + this.localSerializer.serialize(compactReference);
        } else {
            return this.localSerializer.serialize(compactReference);
        }
    }

    private ResourceReference refactor(DocumentResourceReference reference)
    {
        ResourceReference newReference = reference;

        EntityReference entityReference = this.stream.toEntityReference(reference.getReference(), true);
        if (entityReference != null) {
            if (entityReference.getType() == EntityType.ATTACHMENT) {
                newReference = new AttachmentResourceReference(entityReference.getName());
            } else {
                if (this.stream.currentPageReference.equals(entityReference)) {
                    newReference = new DocumentResourceReference("");
                    newReference.setTyped(false);
                } else {
                    newReference = new DocumentResourceReference(compact(entityReference));
                }
                newReference.setParameters(reference.getParameters());
                ((DocumentResourceReference) newReference).setAnchor(reference.getAnchor());
                ((DocumentResourceReference) newReference).setQueryString(reference.getQueryString());
                newReference.setTyped(false);
            }
        }

        return newReference;
    }

    private AttachmentResourceReference refactor(AttachmentResourceReference reference)
    {
        AttachmentResourceReference newReference = reference;

        // Refactor the reference to fit XWiki environment
        EntityReference entityReference = this.stream.toFileEntityReference(reference.getReference());

        if (entityReference != null) {
            entityReference = new EntityReference(reference.getReference(), EntityType.ATTACHMENT, entityReference);
            newReference = new AttachmentResourceReference(compact(entityReference));
            newReference.setParameters(reference.getParameters());
            newReference.setAnchor(reference.getAnchor());
            newReference.setQueryString(reference.getQueryString());
        }

        return newReference;
    }

    @Override
    public void beginLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        // Remember files
        if (this.stream.getProperties().isFileAttached()) {
            this.fileCatcher.beginLink(reference, freestanding, parameters);
        }

        ResourceReference newReference = reference;

        // Refactor the reference if needed
        if (reference instanceof AttachmentResourceReference) {
            newReference = refactor((AttachmentResourceReference) reference);
        } else if (reference instanceof DocumentResourceReference) {
            newReference = refactor((DocumentResourceReference) reference);
        } else if (reference.getType() == ResourceType.URL) {
            if (reference.getReference().startsWith(this.stream.baseURL)) {
                newReference = refactor(
                    new DocumentResourceReference(reference.getReference().substring(this.stream.baseURL.length())));
                freestanding = false;
            }
        }

        this.currentReference.push(newReference);

        super.beginLink(newReference, freestanding, parameters);
    }

    @Override
    public void endLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        super.endLink(this.currentReference.pop(), freestanding, parameters);
    }

    @Override
    public void onImage(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        // Remember files
        if (this.stream.getProperties().isFileAttached()) {
            this.fileCatcher.onImage(reference, freestanding, parameters);
        }

        ResourceReference newReference = reference;

        // Refactor the reference if needed
        if (reference instanceof AttachmentResourceReference) {
            newReference = refactor((AttachmentResourceReference) reference);
            newReference.setTyped(false);
        }

        super.onImage(newReference, freestanding, parameters);
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        // Remember files
        if (this.stream.getProperties().isFileAttached()) {
            this.fileCatcher.onMacro(id, parameters, content, isInline);
        }

        // Convert macros containing wiki content
        // TODO: make it configurable
        String convertedContent = content;
        if (id.equals("gallery") || id.equals("blockquote")) {
            convertedContent = convertWikiContent(convertedContent);
        }

        super.onMacro(id, parameters, convertedContent, isInline);
    }

    private String convertWikiContent(String content)
    {
        String convertedContent = content;

        PrintRenderer renderer = getRenderer();
//        if (renderer != null) {
//            DokuWikiSyntaxInputProperties parserProperties = this.stream.createDokuWikiSyntaxInputProperties(content);
//
//            Listener currentListener = getWrappedListener();
//
//            // Generate events
//            try (BeanInputFilterStream<DokuWikiSyntaxInputProperties> stream =
//                ((BeanInputFilterStreamFactory) this.parserFactory).createInputFilterStream(parserProperties)) {
//                setWrappedListener(renderer);
//
//                stream.read(this);
//
//                convertedContent = renderer.getPrinter().toString();
//            } catch (Exception e) {
//                // TODO log something ?
//            } finally {
//                setWrappedListener(currentListener);
//            }
//        }

        return convertedContent;
    }

    private PrintRenderer getRenderer()
    {
        Syntax syntax = this.targetSyntax;

        if (this.targetSyntax == null) {
            syntax = this.renderingContext.getTargetSyntax();
        }

//        if (syntax != null && !syntax.equals(DokuWikiStreamParser.SYNTAX)) {
//            try {
//                PrintRendererFactory factory =
//                    this.componentManager.getInstance(PrintRendererFactory.class, syntax.toIdString());
//                return factory.createRenderer(new DefaultWikiPrinter());
//            } catch (ComponentLookupException e) {
//                return null;
//            }
//        }

        return null;
    }

    //@Override
    public void onCategory(String name, FilterEventParameters parameters) throws FilterException
    {
        this.categories.add(name);
    }
}
