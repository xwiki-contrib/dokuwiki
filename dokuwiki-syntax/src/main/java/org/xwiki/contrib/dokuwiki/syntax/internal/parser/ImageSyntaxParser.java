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
package org.xwiki.contrib.dokuwiki.syntax.internal.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.listener.reference.AttachmentResourceReference;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.listener.reference.ResourceType;

/**
 * Parse DokuWiki image syntax and send corresponding listener events.
 *
 * @version $Id$
 */
@Component
@Named("image")
@Singleton
public class ImageSyntaxParser implements SingleDokuWikiSyntaxParser
{
    private static final String STYLE_ATTRIBUTE = "style";

    private static final Pattern IMAGE_SIZE_PATTERN = Pattern.compile("(\\d+)(?:x(\\d+))?", Pattern.CASE_INSENSITIVE);

    private static final String QUERY_SEPARATOR = "?";

    private static final String ANCHOR_SEPARATOR = "#";

    private static final String LABEL_SEPARATOR = "|";

    private static final String TAG_WIDTH = "width";

    @Inject
    private MimeTypeDetector mimeTypeDetector;

    @Inject
    private InterWikiReferenceParser interWikiReferenceParser;

    @Override
    public void parse(String imageArgument, Listener listener)
    {
        Map<String, String> param = new HashMap<>();

        String imageName = imageArgument;

        String[] parts = StringUtils.splitByWholeSeparatorPreserveAllTokens(imageName, LABEL_SEPARATOR, 2);
        if (parts.length == 2) {
            //there's a caption
            param.put("alt", parts[1]);
            param.put("title", parts[1]);
            imageName = parts[0];
        }

        maybeSetAlignmentParameter(param, imageName);

        imageName = imageName.trim();

        boolean generateLink = false;
        boolean generateImage = true;

        if (imageName.contains(QUERY_SEPARATOR)) {
            //there's size information
            String params = StringUtils.substringAfterLast(imageName, QUERY_SEPARATOR);
            imageName = StringUtils.substringBeforeLast(imageName, QUERY_SEPARATOR);

            maybeSetSizeParameter(param, params);

            if (StringUtils.containsIgnoreCase(params, "direct")) {
                generateLink = true;
            } else if (StringUtils.containsIgnoreCase(params, "linkonly")) {
                generateLink = true;
                generateImage = false;
            }
        }

        // extract the anchor part of the link
        String anchor;
        if (imageName.contains(ANCHOR_SEPARATOR)) {
            anchor = StringUtils.substringAfterLast(imageName, ANCHOR_SEPARATOR);
            imageName = StringUtils.substringBeforeLast(imageName, ANCHOR_SEPARATOR);
        } else {
            anchor = null;
        }

        // Only generate an image if the mime type is an image
        if (generateImage) {
            String mimeType = this.mimeTypeDetector.detectMimeType(imageName);
            generateImage = mimeType != null && mimeType.startsWith("image/");
            if (!generateImage) {
                // If the mime type is not an image, then we should generate a link
                generateLink = true;
            }
        }

        generateLinkAndImageEvents(listener, param, imageName, generateLink, generateImage, anchor);
    }

    private void generateLinkAndImageEvents(Listener listener, Map<String, String> param, String imageName,
        boolean generateLink, boolean generateImage, String anchor)
    {
        ResourceReference reference = getMediaResourceReference(imageName, anchor);

        if (generateLink) {
            listener.beginLink(reference, false, Listener.EMPTY_PARAMETERS);
        }

        if (generateImage) {
            ResourceReference imageReference = getMediaResourceReference(imageName, anchor);
            // No need to type the attachment reference for images (but do type it for the link as it won't be
            // recognized otherwise).
            if (ResourceType.ATTACHMENT.equals(imageReference.getType())) {
                imageReference.setTyped(false);
            }
            listener.onImage(imageReference, false, param);
        }

        if (generateLink) {
            listener.endLink(reference, false, Listener.EMPTY_PARAMETERS);
        }
    }

    private ResourceReference getMediaResourceReference(String imageName, String anchor)
    {
        ResourceReference reference;
        String imageNameWithAnchor = anchor != null ? imageName + ANCHOR_SEPARATOR + anchor : imageName;
        if (isHttpOrFtpURL(imageName)) {
            reference = new ResourceReference(imageNameWithAnchor, ResourceType.URL);
            reference.setTyped(false);
        } else if (this.interWikiReferenceParser.isInterWikiReference(imageName)) {
            reference = this.interWikiReferenceParser.parse(imageNameWithAnchor);
        } else {
            AttachmentResourceReference attachmentResourceReference = new AttachmentResourceReference(imageName);
            if (anchor != null) {
                attachmentResourceReference.setAnchor(anchor);
            }
            reference = attachmentResourceReference;
        }
        return reference;
    }

    private static boolean isHttpOrFtpURL(String imageName)
    {
        return StringUtils.startsWithIgnoreCase(imageName, "http://")
            || StringUtils.startsWithIgnoreCase(imageName, "https://")
            || StringUtils.startsWithIgnoreCase(imageName, "ftp://");
    }

    private static void maybeSetSizeParameter(Map<String, String> param, String parameterString)
    {
        Matcher sizeMatcher = IMAGE_SIZE_PATTERN.matcher(parameterString);
        if (sizeMatcher.find()) {
            param.put(TAG_WIDTH, sizeMatcher.group(1));
            if (sizeMatcher.group(2) != null) {
                param.put("height", sizeMatcher.group(2));
            }
        }
    }

    private static void maybeSetAlignmentParameter(Map<String, String> param, String imageName)
    {
        if (imageName.startsWith(" ") && imageName.endsWith(" ")) {
            //align centre
            param.put(STYLE_ATTRIBUTE, "display: block; margin-left: auto; margin-right: auto;");
        } else if (imageName.startsWith(" ")) {
            //align left
            param.put(STYLE_ATTRIBUTE, "float: left;");
        } else if (imageName.endsWith(" ")) {
            //align right
            param.put(STYLE_ATTRIBUTE, "float: right;");
        }
    }
}
