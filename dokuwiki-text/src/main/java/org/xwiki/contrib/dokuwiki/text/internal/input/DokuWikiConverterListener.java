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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.rendering.listener.WrappingListener;
import org.xwiki.rendering.listener.reference.DocumentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * Convert DokuWiki content like links to their XWiki equivalent.
 *
 * @version $Id$
 * @since 1.4
 */
@Component(roles = DokuWikiConverterListener.class)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiConverterListener extends WrappingListener
{
    private static final Pattern EXTERNAL_URL_PATTERN =
        Pattern.compile("^([a-z0-9\\-.+]+?)://", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("^\\S+@\\S+$");

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

    @Inject
    private DokuWikiDeaccent deaccent;

    private List<String> dokuWikiPath;

    /**
     * @param dokuWikiPath the path to the DokuWiki page reference (e.g., "parenSpace", "space", "page")
     */
    public void setDokuWikiPath(List<String> dokuWikiPath)
    {
        this.dokuWikiPath = dokuWikiPath;
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

    private ResourceReference convertLink(ResourceReference reference, boolean freestanding)
    {
        ResourceReference result;

        if (reference.getType() != ResourceType.INTERWIKI && !freestanding) {
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
        } else {
            result = reference;
        }

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

        // Replace "/" and ";" by ":" in the link target to conform with DokuWiki cleaning.
        cleanedLinkTarget = cleanedLinkTarget.replace('/', ':').replace(';', ':');

        cleanedLinkTarget = resolvePrefix(cleanedLinkTarget);

        List<String> idParts = resolveRelatives(cleanedLinkTarget);

        if (idParts.isEmpty()) {
            idParts.add(DOKUWIKI_NAMESPACE_INDEX);
        }

        idParts = idParts.stream().map(this::cleanIDPart).filter(StringUtils::isNotBlank).collect(Collectors.toList());

        if (!idParts.isEmpty() && DOKUWIKI_NAMESPACE_INDEX.equals(idParts.get(idParts.size() - 1))) {
            idParts.remove(idParts.size() - 1);
        }

        if (idParts.isEmpty()) {
            idParts.add("Main");
        }

        idParts.add("WebHome");

        result.setReference(String.join(DOT, idParts));

        return result;
    }

    private String resolvePrefix(String cleanedLinkTarget)
    {
        String id = cleanedLinkTarget;
        // "~" means relative to the current page.
        if (id.startsWith("~")) {
            id = String.join(NAMESPACE_SEPARATOR, this.dokuWikiPath) + NAMESPACE_SEPARATOR + id.substring(1);
        }

        String contextNamespace;
        if (this.dokuWikiPath.size() > 1) {
            contextNamespace = String.join(NAMESPACE_SEPARATOR, this.dokuWikiPath.subList(0,
                this.dokuWikiPath.size() - 1));
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

        id = SPECIAL_PATTERN.matcher(id).replaceAll("");

        id = REPEATED_UNDERSCORE_PATTERN.matcher(id).replaceAll("_");

        id = StringUtils.strip(id, ":._-");

        return id;
    }
}
