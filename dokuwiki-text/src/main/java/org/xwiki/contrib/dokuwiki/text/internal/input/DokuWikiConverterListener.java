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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;

/**
 * Convert DokuWiki content like links to their XWiki equivalent.
 *
 * @version $Id$
 * @since 2.0
 */
@Component(roles = DokuWikiConverterListener.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiConverterListener extends WrappingListener
{
    private static final Pattern EXTERNAL_URL_PATTERN =
        Pattern.compile("^([a-z0-9\\-.+]+?)://", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("^\\S+@\\S+(?:\\?.*)?$");

    private static final String NAMESPACE_SEPARATOR = ":";

    // This pattern differs slightly from DokuWiki by making the first group possessive. This is to avoid recursion
    // and should not change the behavior.
    private static final Pattern INITIAL_DOT_NORMALIZATION_PATTERN = Pattern.compile("^((\\.+:)*+)(\\.+)(?=[^:.])");

    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[^\\p{L}\\p{N}_:.-]");

    private static final Pattern REPEATED_UNDERSCORE_PATTERN = Pattern.compile("_+");

    private static final String QUERY_SEPARATOR = "?";

    private static final String DOKUWIKI_NAMESPACE_INDEX = "start";

    private static final String DOT = ".";

    private static final String ANCHOR_SEPARATOR = "#";

    private static final String UNDERSCORE = "_";

    @Inject
    private DokuWikiDeaccent deaccent;

    @Inject
    private DokuWikiReferenceConverter referenceConverter;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named(org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser.SYNTAX_STRING)
    private StreamParser nestedParser;

    @Inject
    @Named("xwiki/2.1")
    private PrintRendererFactory xwiki21Factory;

    private String dokuWikiReference;

    /**
     * @param dokuWikiReference the DokuWiki reference of the current page
     */
    public void setDokuWikiReference(String dokuWikiReference)
    {
        this.dokuWikiReference = dokuWikiReference;
    }

    @Override
    public void onMacro(String id, Map<String, String> parameters, String content, boolean inline)
    {
        String convertedContent = content;

        if ("footnote".equals(id)) {
            Listener oldListener = getWrappedListener();
            try (StringReader contentReader = new StringReader(content)) {
                // We need to convert the footnote content to XWiki syntax
                DefaultWikiPrinter printer = new DefaultWikiPrinter();
                PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
                // Re-use this listener with a new printer as this listener has no state.
                setWrappedListener(renderer);
                this.nestedParser.parse(contentReader, this);
                convertedContent = renderer.getPrinter().toString();
            } catch (ParseException e) {
                // Ignore, the conversion failed. We'll use the original content.
            } finally {
                this.setWrappedListener(oldListener);
            }
        }

        super.onMacro(id, parameters, convertedContent, inline);
    }

    @Override
    public void beginLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        super.beginLink(convertLink(reference, freestanding), freestanding, parameters);
    }

    @Override
    public void endLink(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        super.endLink(convertLink(reference, freestanding), freestanding, parameters);
    }

    @Override
    public void onImage(ResourceReference reference, boolean freestanding, Map<String, String> parameters)
    {
        ResourceReference resolvedReference;
        if (reference.getType() == ResourceType.ATTACHMENT) {
            // Convert DokuWiki media reference to XWiki attachment reference
            resolvedReference = resolveDokuWikiMediaReferenceToAttachmentReference(reference);
        } else {
            resolvedReference = reference;
        }

        super.onImage(resolvedReference, freestanding, parameters);
    }

    private ResourceReference convertLink(ResourceReference reference, boolean freestanding)
    {
        ResourceReference result;

        if (reference.getType() == ResourceType.URL && !freestanding) {
            String linkTarget = reference.getReference().trim();
            if (EXTERNAL_URL_PATTERN.matcher(linkTarget).find()) {
                // External link as defined in DokuWiki.
                // Just keep the reference as-is as it will be converted to an external link in XWiki automatically.
                result = reference;
            } else if (EMAIL_ADDRESS_PATTERN.matcher(linkTarget).find()) {
                // Mailto link
                result = new ResourceReference(linkTarget, ResourceType.MAILTO);
            } else if (StringUtils.isBlank(linkTarget) || linkTarget.startsWith(ANCHOR_SEPARATOR)) {
                // Link to a section of the current page
                DocumentResourceReference documentResourceReference = new DocumentResourceReference("");
                if (linkTarget.length() > 1) {
                    documentResourceReference.setAnchor(linkTarget.substring(1));
                }
                result = documentResourceReference;
            } else {
                // Link to a page in the wiki
                result = resolveDokuWikiReference(linkTarget);
            }
        } else if (reference.getType() == ResourceType.ATTACHMENT) {
            result = resolveDokuWikiMediaReferenceToAttachmentReference(reference);
        } else {
            result = reference;
        }

        return result;
    }

    private ResourceReference resolveDokuWikiMediaReferenceToAttachmentReference(ResourceReference reference)
    {
        ResourceReference result;
        String linkTarget = reference.getReference().trim();
        String cleanedLinkTarget = resolveAndCleanDokuWikiId(linkTarget);

        // Split into parts again to construct the reference to the page that contains the attachment.
        String[] linkTargetParts = StringUtils.split(cleanedLinkTarget, NAMESPACE_SEPARATOR);
        List<String> linkTargetPartsList = new ArrayList<>(Arrays.asList(linkTargetParts));

        // The last part is the attachment name.
        String attachmentName = linkTargetPartsList.remove(linkTargetPartsList.size() - 1);

        linkTargetPartsList.add(DOKUWIKI_NAMESPACE_INDEX);
        String pageId = StringUtils.join(linkTargetPartsList, NAMESPACE_SEPARATOR);
        LocalDocumentReference pageReference = this.referenceConverter.getDocumentReference(pageId);
        DocumentReference documentReference = new DocumentReference(pageReference, new WikiReference("xwiki"));
        AttachmentReference attachmentReference = new AttachmentReference(attachmentName, documentReference);
        String absoluteAttachmentReference = this.serializer.serialize(attachmentReference);
        String relativeAttachmentReference = StringUtils.removeStart(absoluteAttachmentReference, "xwiki:");
        result = reference.clone();
        result.setReference(relativeAttachmentReference);
        return result;
    }

    private ResourceReference resolveDokuWikiReference(String linkTarget)
    {
        String cleanedLinkTarget;
        DocumentResourceReference result = new DocumentResourceReference("");

        if (linkTarget.contains(QUERY_SEPARATOR)) {
            String[] linkTargetParts = StringUtils.split(linkTarget, QUERY_SEPARATOR, 2);
            cleanedLinkTarget = linkTargetParts[0];
            result.setQueryString(linkTargetParts[1]);
        } else {
            cleanedLinkTarget = linkTarget;
        }

        if (cleanedLinkTarget.contains(ANCHOR_SEPARATOR)) {
            String[] linkTargetParts = StringUtils.split(cleanedLinkTarget, ANCHOR_SEPARATOR, 2);
            cleanedLinkTarget = linkTargetParts[0];
            String anchor = linkTargetParts[1];
            if (StringUtils.isNotBlank(anchor)) {
                result.setAnchor(anchor);
            }
        }

        String cleanedId = resolveAndCleanDokuWikiId(cleanedLinkTarget);

        LocalDocumentReference localDocumentReference = this.referenceConverter.getDocumentReference(cleanedId);
        result.setReference(this.serializer.serialize(localDocumentReference));

        return result;
    }

    private String resolveAndCleanDokuWikiId(String linkTarget)
    {
        // Replace "/" and ";" by ":" in the link target to conform with DokuWiki cleaning.
        String cleanedLinkTarget = linkTarget.replace('/', ':').replace(';', ':');

        cleanedLinkTarget = resolvePrefix(cleanedLinkTarget);

        List<String> idParts = resolveRelatives(cleanedLinkTarget);

        if (idParts.isEmpty()) {
            idParts.add(DOKUWIKI_NAMESPACE_INDEX);
        }

        return idParts.stream()
            .map(this::cleanIDPart)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(NAMESPACE_SEPARATOR));
    }

    private String resolvePrefix(String cleanedLinkTarget)
    {
        String id = cleanedLinkTarget;
        // "~" means relative to the current page.
        if (id.startsWith("~")) {
            id = this.dokuWikiReference + NAMESPACE_SEPARATOR + id.substring(1);
        }

        String contextNamespace;
        if (this.dokuWikiReference.contains(NAMESPACE_SEPARATOR)) {
            contextNamespace = StringUtils.substringBeforeLast(this.dokuWikiReference, NAMESPACE_SEPARATOR);
        } else {
            contextNamespace = "";
        }

        if (id.startsWith(DOT)) {
            // Relative to the current namespace.
            id = INITIAL_DOT_NORMALIZATION_PATTERN.matcher(id).replaceFirst("$1$3:");
            id = contextNamespace + NAMESPACE_SEPARATOR + id;
        }

        // auto-relative to the current namespace.
        if (!id.contains(NAMESPACE_SEPARATOR)) {
            id = contextNamespace + NAMESPACE_SEPARATOR + id;
        }

        return id;
    }

    private List<String> resolveRelatives(String id)
    {
        List<String> result = new ArrayList<>();

        for (String part : id.split(NAMESPACE_SEPARATOR)) {
            if (part.equals("..")) {
                if (!result.isEmpty()) {
                    result.remove(result.size() - 1);
                }
            } else if (!part.equals(DOT)) {
                result.add(part);
            }
        }

        return result;
    }

    private String cleanIDPart(String input)
    {
        String id = input.trim().toLowerCase();

        // This assumes the default setting, i.e., $conf['deaccent'] = 1 in DokuWiki
        id = this.deaccent.deaccent(id);

        id = SPECIAL_PATTERN.matcher(id).replaceAll(UNDERSCORE);

        id = REPEATED_UNDERSCORE_PATTERN.matcher(id).replaceAll(UNDERSCORE);

        id = StringUtils.strip(id, ":._-");

        return id;
    }
}
