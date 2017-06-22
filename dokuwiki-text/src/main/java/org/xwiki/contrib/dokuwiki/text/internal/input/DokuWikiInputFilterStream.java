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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
//import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties;
//import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties.ReferenceType;
//import org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser;
import org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties;
import org.xwiki.contrib.dokuwiki.text.internal.DokuWikiFilter;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.event.model.WikiDocumentFilter;
import org.xwiki.filter.event.model.WikiObjectFilter;
import org.xwiki.filter.input.AbstractBeanInputFilterStream;
import org.xwiki.filter.input.FileInputSource;
import org.xwiki.filter.input.InputFilterStreamFactory;
import org.xwiki.filter.input.InputSource;
import org.xwiki.model.EntityType;
import org.xwiki.model.ModelConfiguration;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.renderer.PrintRendererFactory;
/**
 * @version $Id: 41df1dab66b03111214dbec56fee8dbd44747638 $
 */
@Component
@Named(DokuWikiInputProperties.FILTER_STREAM_TYPE_STRING)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiInputFilterStream extends AbstractBeanInputFilterStream<DokuWikiInputProperties, DokuWikiFilter>
{
    //private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

    private static final String TAG_SITEINFO = "siteinfo";

    private static final String TAG_SITEINFO_NAMESPACES = "namespaces";

    private static final String TAG_SITEINFO_NAMESPACE = "namespace";

    private static final String TAG_SITEINFO_BASE = "base";

    private static final String TAG_PAGE = "page";

    private static final String TAG_PAGE_TITLE = "title";

    private static final String TAG_PAGE_REVISION = "revision";

    private static final String TAG_PAGE_REVISION_CONTRIBUTOR = "contributor";

    private static final String TAG_PAGE_REVISION_CONTRIBUTOR_USERNAME = "username";

    private static final String TAG_PAGE_REVISION_CONTRIBUTOR_IP = "ip";

    private static final String TAG_PAGE_REVISION_TIMESTAMP = "timestamp";

    private static final String TAG_PAGE_REVISION_MINOR = "minor";

    private static final String TAG_PAGE_REVISION_VERSION = "id";

    private static final String TAG_PAGE_REVISION_COMMENT = "comment";

    private static final String TAG_PAGE_REVISION_CONTENT = "text";

    private static final String REFERENCE_TAGCLASS = "XWiki.TagClass";

    /**
     * This is not final, it gets initialized right after the base url is read from the text, with the precise value from
     * the XML.
     */
    private String mainPageName = "Main_Page";

    @Inject
    private Logger logger;

//    @Inject
//    @Named(DokuWikiSyntaxInputProperties.FILTER_STREAM_TYPE_STRING)
    private InputFilterStreamFactory parserFactory;

    @Inject
    private ModelConfiguration modelConfiguration;

    @Inject
    private Provider<DokuWikiContextConverterListener> listenerProvider;

    @Inject
    @Named("xwiki/2.1")
    private PrintRendererFactory xwiki21Factory;

    private DokuWikiNamespaces namespaces = new DokuWikiNamespaces();

    private Set<String> currentFiles;

    private Set<String> currentCategories;

    String currentPageTitle;

    EntityReference previousParentReference;

    EntityReference currentParentReference;

    EntityReference currentPageReference;

    String baseURL;

    DokuWikiInputProperties getProperties()
    {
        return this.properties;
    }

    EntityReference toEntityReference(String reference, boolean link)
    {
        String pageName = reference;
        String namespace;

        // Separate namespace and page name
        int index = pageName.indexOf(':');
        if (index > 0) {
            namespace = this.namespaces.resolve(pageName.substring(0, index));
            if (!this.properties.isOnlyRegisteredNamespaces() || this.namespaces.isNamespace(namespace)) {
                pageName = pageName.substring(index + 1);
            } else {
                namespace = null;
            }
        } else {
            namespace = null;
        }

        if (link) {
            // DokuWiki actually assume the link reference is a partial URL (it just concatenate it to the base URL) so
            // we have to decode it
            try {
                pageName = URLDecoder.decode(pageName, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // Should never happen
            }
        }

        // DokuWiki replace the white spaces with an underscore in the URL
        pageName = pageName.replace(' ', '_');

        // Maybe convert DokuWiki home page name into XWiki home page name
        if (this.properties.isConvertToXWiki() && pageName.equals(this.mainPageName)) {
            pageName = this.modelConfiguration.getDefaultReferenceValue(EntityType.DOCUMENT);
        }

        // Clean page name if required
        if (StringUtils.isNotEmpty(this.properties.getForbiddenCharacters())) {
            for (int i = 0; i < this.properties.getForbiddenCharacters().length(); ++i) {
                pageName = StringUtils.remove(pageName, this.properties.getForbiddenCharacters().charAt(i));
            }
        }

        // Find page parent reference
        EntityReference parentReference;
        if (namespace != null) {
            if (this.namespaces.isFileNamespace(namespace)) {
                if (link && this.properties.isFileAttached()) {
                    return new EntityReference(pageName, EntityType.ATTACHMENT);
                } else {
                    return toFileEntityReference(pageName);
                }
            } else if (this.namespaces.isSpecialNamespace(namespace)) {
                return null;
            } else {
                parentReference = new EntityReference(namespace, EntityType.SPACE, this.properties.getParent());
            }
        } else {
            if (this.properties.getParent() == null) {
                parentReference =
                    new EntityReference(this.modelConfiguration.getDefaultReferenceValue(EntityType.SPACE),
                        EntityType.SPACE, this.properties.getParent());
            } else {
                parentReference = this.properties.getParent();
            }
        }

        // If root of the wiki make it Main space
        if (parentReference.getType() == EntityType.WIKI && this.properties.isConvertToXWiki()) {
            parentReference = new EntityReference(this.modelConfiguration.getDefaultReferenceValue(EntityType.SPACE),
                EntityType.SPACE, parentReference);
        }

        // Split by space separators
        if (StringUtils.isNotEmpty(this.properties.getSpaceSeparator())) {
            String[] elements = pageName.split(this.properties.getSpaceSeparator());
            if (elements.length > 1) {
                for (int i = 0; i < elements.length - 1; ++i) {
                    parentReference = new EntityReference(elements[i], EntityType.SPACE, parentReference);
                }
                pageName = elements[elements.length - 1];
                if (pageName.isEmpty()) {
                    pageName = this.modelConfiguration.getDefaultReferenceValue(EntityType.DOCUMENT);
                }
            }
        }

        return new EntityReference(pageName, EntityType.DOCUMENT, parentReference);
    }

    EntityReference toFileEntityReference(String pageName)
    {
        // Don't import File namespace page if files are attached to each page using it
        if (this.properties.isFileAttached()) {
            return null;
        }

        if (StringUtils.isEmpty(pageName)) {
            return null;
        }

        EntityReference parentReference = this.properties.getFileSpace();
        if (parentReference != null) {
            if (parentReference.extractFirstReference(EntityType.WIKI) == null) {
                parentReference = new EntityReference(parentReference, this.properties.getParent());
            }
        } else {
            // By default put files in a space with the names of the defined file namespace
            parentReference =
                new EntityReference(this.namespaces.getFileNamespace(), EntityType.SPACE, this.properties.getParent());
        }

        return new EntityReference(pageName, EntityType.DOCUMENT, parentReference);
    }

    @Override
    public void close() throws IOException
    {
//        this.properties.getSource().close();
    }

    @Override
    protected void read(Object filter, DokuWikiFilter proxyFilter) throws FilterException
    {
//        // Create reader
//        XMLStreamReader xmlReader;
//        try {
//            xmlReader = getXMLStreamReader();
//        } catch (Exception e) {
//            throw new FilterException("Failed to create XMLStreamReader", e);
//        }
//
//        // Read document
//        try {
//            read(xmlReader, filter, proxyFilter);
//        } catch (Exception e) {
//            throw new FilterException("Failed to parse XML", e);
//        }
    }


    private void read(XMLStreamReader xmlReader, Object filter, DokuWikiFilter proxyFilter)
        throws XMLStreamException, FilterException, IOException
    {
        xmlReader.nextTag();

        readDokuWiki(xmlReader, filter, proxyFilter);
    }

    private void readDokuWiki(XMLStreamReader xmlReader, Object filter, DokuWikiFilter proxyFilter)
        throws XMLStreamException, FilterException, IOException
    {
//        for (xmlReader.nextTag(); xmlReader.isStartElement(); xmlReader.nextTag()) {
//            String elementName = xmlReader.getLocalName();
//
//            if (elementName.equals(TAG_SITEINFO)) {
//                readSiteInfo(xmlReader);
//            } else if (elementName.equals(TAG_PAGE)) {
//                readPage(xmlReader, filter, proxyFilter);
//            } else {
//                StAXUtils.skipElement(xmlReader);
//            }
//        }
//
//        this.currentParentReference = null;
//
//        // Send parent events
//        sendSpaceEvents(proxyFilter);
    }

    private void readSiteInfo(XMLStreamReader xmlReader) throws XMLStreamException
    {
//        for (xmlReader.nextTag(); xmlReader.isStartElement(); xmlReader.nextTag()) {
//            String elementName = xmlReader.getLocalName();
//
//            if (elementName.equals(TAG_SITEINFO_NAMESPACES)) {
//                readNamespaces(xmlReader);
//            } else if (elementName.equals(TAG_SITEINFO_BASE)) {
//                readBaseURLInfo(xmlReader);
//            } else {
//                StAXUtils.skipElement(xmlReader);
//            }
//        }
    }

    private void readNamespaces(XMLStreamReader xmlReader) throws XMLStreamException
    {
//        for (xmlReader.nextTag(); xmlReader.isStartElement(); xmlReader.nextTag()) {
//            String elementName = xmlReader.getLocalName();
//
//            if (elementName.equals(TAG_SITEINFO_NAMESPACE)) {
//                this.namespaces.addNamespace(xmlReader.getAttributeValue(null, "key"), xmlReader.getElementText());
//            } else {
//                StAXUtils.skipElement(xmlReader);
//            }
//        }
    }

    private void readBaseURLInfo(XMLStreamReader xmlReader) throws XMLStreamException
    {
        this.baseURL = xmlReader.getElementText();
        if (!StringUtils.isEmpty(this.baseURL)) {
            int lastSlash = this.baseURL.lastIndexOf('/');
            // if a last slash even exists, and some string is still left after it in the string, extract that string
            // and use it as homepage name
            if (lastSlash > 0 && (lastSlash + 1 < this.baseURL.length())) {
                this.mainPageName = this.baseURL.substring(lastSlash + 1);
                this.baseURL = this.baseURL.substring(0, lastSlash + 1);
            }
        }
    }

    private void readPage(XMLStreamReader xmlReader, Object filter, DokuWikiFilter proxyFilter)
        throws XMLStreamException, FilterException, IOException
    {
//        boolean skip = false;
//
//        for (xmlReader.nextTag(); xmlReader.isStartElement(); xmlReader.nextTag()) {
//            String elementName = xmlReader.getLocalName();
//
//            if (skip) {
//                StAXUtils.skipElement(xmlReader);
//            } else if (elementName.equals(TAG_PAGE_TITLE)) {
//                this.currentPageTitle = xmlReader.getElementText();
//
//                // Find current page reference
//                this.currentPageReference = toEntityReference(this.currentPageTitle, false);
//
//                if (this.currentPageReference != null) {
//                    if (this.properties.isConvertToXWiki() && !this.properties.isTerminalPages()
//                        && !this.namespaces.isInFileNamespace(this.currentPageTitle)) {
//                        // Make the page a non terminal page
//                        String defaultPageName = this.modelConfiguration.getDefaultReferenceValue(EntityType.DOCUMENT);
//                        if (!this.currentPageReference.getName().equals(defaultPageName)) {
//                            this.currentPageReference = new EntityReference(this.currentPageReference.getName(),
//                                EntityType.SPACE, this.currentPageReference.getParent());
//                            this.currentPageReference = new EntityReference(
//                                this.modelConfiguration.getDefaultReferenceValue(EntityType.DOCUMENT),
//                                EntityType.DOCUMENT, this.currentPageReference);
//                        }
//                    }
//
//                    this.currentParentReference = this.currentPageReference.getParent();
//
//                    // Send parent events
//                    sendSpaceEvents(proxyFilter);
//
//                    // Send document event
//                    proxyFilter.beginWikiDocument(this.currentPageReference.getName(), FilterEventParameters.EMPTY);
//                    proxyFilter.beginWikiDocumentLocale(Locale.ROOT, FilterEventParameters.EMPTY);
//                } else {
//                    skip = true;
//                }
//            } else if (elementName.equals(TAG_PAGE_REVISION)) {
//                readPageRevision(xmlReader, filter, proxyFilter);
//            } else {
//                StAXUtils.skipElement(xmlReader);
//            }
//        }
//
//        this.currentPageReference = null;
//
//        if (!skip) {
//            proxyFilter.endWikiDocumentLocale(Locale.ROOT, FilterEventParameters.EMPTY);
//            proxyFilter.endWikiDocument(this.currentParentReference.getName(), FilterEventParameters.EMPTY);
//        }
    }

    private void sendSpaceEvents(DokuWikiFilter proxyFilter) throws FilterException
    {
        // Close spaces that need to be closed
        if (this.previousParentReference != null) {
            sendEndParents(proxyFilter);
        }

        // Open spaces that need to be open
        if (this.currentParentReference != null) {
            sendBeginParents(proxyFilter);
        }
    }

    private void sendEndParents(DokuWikiFilter proxyFilter) throws FilterException
    {
        List<EntityReference> previousParents = this.previousParentReference.getReversedReferenceChain();
        List<EntityReference> currentParents = this.currentParentReference != null
            ? this.currentParentReference.getReversedReferenceChain() : Collections.<EntityReference>emptyList();

        // Find the first different level
        int i = 0;
        while (i < previousParents.size() && i < currentParents.size()) {
            if (!currentParents.get(i).equals(previousParents.get(i))) {
                break;
            }

            ++i;
        }

        if (i < previousParents.size()) {
            // Delete what is different
            for (int diff = previousParents.size() - i; diff > 0; --diff, this.previousParentReference =
                this.previousParentReference.getParent()) {
                if (this.previousParentReference.getType() == EntityType.WIKI) {
                    proxyFilter.endWiki(this.previousParentReference.getName(), FilterEventParameters.EMPTY);
                } else {
                    proxyFilter.endWikiSpace(this.previousParentReference.getName(), FilterEventParameters.EMPTY);
                }
            }
        }
    }

    private void sendBeginParents(DokuWikiFilter proxyFilter) throws FilterException
    {
        int previousSize = this.previousParentReference != null ? this.previousParentReference.size() : 0;
        int currentSize = this.currentParentReference.size();

        int diff = currentSize - previousSize;

        if (diff > 0) {
            List<EntityReference> parents = this.currentParentReference.getReversedReferenceChain();
            for (int i = parents.size() - diff; i < parents.size(); ++i) {
                EntityReference parent = parents.get(i);
                if (parent.getType() == EntityType.WIKI) {
                    proxyFilter.beginWiki(parent.getName(), FilterEventParameters.EMPTY);
                } else {
                    proxyFilter.beginWikiSpace(parent.getName(), FilterEventParameters.EMPTY);
                }

                this.previousParentReference =
                    new EntityReference(parent.getName(), parent.getType(), this.previousParentReference);
            }
        }
    }

//    public DokuWikiSyntaxInputProperties createDokuWikiSyntaxInputProperties(String content)
//    {
//        DokuWikiSyntaxInputProperties parserProperties = new DokuWikiSyntaxInputProperties();
//
//        // Set source
//        parserProperties.setSource(new StringInputSource(content));
//
//        // Make sure to keep source references unchanged
//        parserProperties.setReferenceType(ReferenceType.DOKUWIKI);
//
//        // Set namespaces
//        parserProperties.setCustomNamespaces(this.namespaces.getNamespaces());
//
//        // Set toc mode
//        parserProperties.setNoToc(this.properties.isNoToc());
//
//        return parserProperties;
//    }

    private void readPageRevision(XMLStreamReader xmlReader, Object filter, DokuWikiFilter proxyFilter)
        throws XMLStreamException, FilterException, IOException
    {
//        FilterEventParameters pageRevisionParameters = new FilterEventParameters();
//
//        // Defaults
//        pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_REVISION_MINOR, false);
//
//        pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_TITLE, this.currentPageTitle);
//
//        String version = "1";
//
//        boolean beginWikiDocumentRevisionSent = false;
//
//        this.currentFiles = Collections.emptySet();
//
//        for (xmlReader.nextTag(); xmlReader.isStartElement(); xmlReader.nextTag()) {
//            String elementName = xmlReader.getLocalName();
//
//            if (elementName.equals(TAG_PAGE_REVISION_VERSION)) {
//                version = xmlReader.getElementText();
//            } else if (elementName.equals(TAG_PAGE_REVISION_COMMENT)) {
//                pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_REVISION_COMMENT, xmlReader.getElementText());
//            } else if (elementName.equals(TAG_PAGE_REVISION_CONTENT)) {
//                String content = xmlReader.getElementText();
//
////                if (this.properties.isContentEvents() && filter instanceof Listener) {
////                    // Begin document revision
////                    proxyFilter.beginWikiDocumentRevision(version, pageRevisionParameters);
////                    beginWikiDocumentRevisionSent = true;
////
////                    DokuWikiSyntaxInputProperties parserProperties = createDokuWikiSyntaxInputProperties(content);
////
////                    // Refactor references and find attachments
////                    DokuWikiContextConverterListener listener = this.listenerProvider.get();
////                    listener.initialize(proxyFilter, this, null);
////
////                    // Generate events
////                    try (BeanInputFilterStream<DokuWikiSyntaxInputProperties> stream =
////                        ((BeanInputFilterStreamFactory) this.parserFactory).createInputFilterStream(parserProperties)) {
////                        stream.read(listener);
////                    }
////
////                    // Remember linked files
////                    this.currentFiles = listener.getFiles();
////                } else if (this.properties.isConvertToXWiki()) {
////                    // Convert content to XWiki syntax
////                    pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertToXWiki21(content));
////                    pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_SYNTAX, Syntax.XWIKI_2_1);
////                } else {
////                    // Keep DokuWiki syntax
////                    pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, content);
////                    pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_SYNTAX, DokuWikiStreamParser.SYNTAX);
////                }
//            } else if (elementName.equals(TAG_PAGE_REVISION_MINOR)) {
//                pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_REVISION_MINOR, true);
//                StAXUtils.skipElement(xmlReader);
//            } else if (elementName.equals(TAG_PAGE_REVISION_TIMESTAMP)) {
//                try {
//                    Date date = DatatypeFactory.newInstance().newXMLGregorianCalendar(xmlReader.getElementText())
//                        .toGregorianCalendar().getTime();
//                    pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_REVISION_DATE, date);
//                } catch (DatatypeConfigurationException e) {
//                    this.logger.error("Failed to create DatatypeFactory instance", e);
//                }
//            } else if (elementName.equals(TAG_PAGE_REVISION_CONTRIBUTOR)) {
//                pageRevisionParameters.put(WikiDocumentFilter.PARAMETER_REVISION_AUTHOR, getPageContributor(xmlReader));
//            } else {
//                StAXUtils.skipElement(xmlReader);
//            }
//        }
//
//        if (!beginWikiDocumentRevisionSent) {
//            proxyFilter.beginWikiDocumentRevision(version, pageRevisionParameters);
//        }
//
//        // It might be a page dedicated to a file
//        String filename = this.namespaces.getFileName(this.currentPageTitle);
//        if (filename != null) {
//            this.currentFiles.add(filename);
//        }
//
//        // Generate tags for categories
//        if (!this.currentCategories.isEmpty()) {
//            sendCategories(this.currentCategories, proxyFilter);
//        }
//
//        // Attach files if any
//        if (!this.currentFiles.isEmpty()) {
//            for (String fileName : this.currentFiles) {
//                sendAttachment(fileName, proxyFilter);
//            }
//        }
//        // Reset files
//        this.currentFiles = null;
//
//        proxyFilter.endWikiDocumentRevision(version, pageRevisionParameters);
    }

//    private String convertToXWiki21(String content) throws FilterException
//    {
//        DefaultWikiPrinter printer = new DefaultWikiPrinter();
//        PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
//
//        DokuWikiSyntaxInputProperties parserProperties = createDokuWikiSyntaxInputProperties(content);
//
//        // Refactor references and find attachments
//        DokuWikiContextConverterListener listener = this.listenerProvider.get();
//        listener.initialize(renderer, this, this.xwiki21Factory.getSyntax());
//
//        // Generate events
//        try (BeanInputFilterStream<DokuWikiSyntaxInputProperties> stream =
//            ((BeanInputFilterStreamFactory) this.parserFactory).createInputFilterStream(parserProperties)) {
//            stream.read(listener);
//        } catch (Exception e) {
//            throw new FilterException("Failed to convert content page", e);
//        }
//
//        this.currentFiles = listener.getFiles();
//        this.currentCategories = listener.getCategories();
//
//        return printer.toString();
//    }

    private void sendAttachment(String fileName, DokuWikiFilter proxyFilter) throws FilterException, IOException
    {
//        File file = getFile(fileName);
//
//        if (file != null) {
//            try (InputStream streamToClose = new FileInputStream(file)) {
//                proxyFilter.onWikiAttachment(fileName, streamToClose, file.length(), FilterEventParameters.EMPTY);
//            }
//        }
    }

    private File getFile(String fileName) throws FilterException
    {
        if (StringUtils.isNotEmpty(fileName)) {
            InputSource files = this.properties.getFiles();

            if (files instanceof FileInputSource) {
                File folder = ((FileInputSource) files).getFile();

                String md5Hex = DigestUtils.md5Hex(fileName).substring(0, 2);
                String folderName1 = md5Hex.substring(0, 1);
                String folderName2 = md5Hex.substring(0, 2);

                File folder1 = new File(folder, folderName1);
                File folder2 = new File(folder1, folderName2);

                File file = new File(folder2, fileName);

                if (file.exists() && file.isFile()) {
                    return file;
                }

                this.logger.warn("Can't find file [{}]", file.getAbsolutePath());
            } else {
                throw new FilterException("Unsupported input source [" + files.getClass() + "] ([" + files + "])");
            }
        }

        return null;
    }

    private void sendCategories(Set<String> categories, DokuWikiFilter proxyFilter) throws FilterException
    {
        FilterEventParameters objectParameters = new FilterEventParameters();
        objectParameters.put(WikiObjectFilter.PARAMETER_CLASS_REFERENCE, REFERENCE_TAGCLASS);

        proxyFilter.beginWikiObject(REFERENCE_TAGCLASS, objectParameters);

        // Tags class definition
        // TODO: Remove when https://jira.xwiki.org/browse/XWIKI-14061 is fixed (9.2+)
        proxyFilter.beginWikiClass(FilterEventParameters.EMPTY);
        proxyFilter.beginWikiClassProperty("tags", "StaticList", FilterEventParameters.EMPTY);
        proxyFilter.onWikiClassPropertyField("multiSelect", "1", FilterEventParameters.EMPTY);
        proxyFilter.onWikiClassPropertyField("relationalStorage", "1", FilterEventParameters.EMPTY);
        proxyFilter.endWikiClassProperty("tags", "StaticList", FilterEventParameters.EMPTY);
        proxyFilter.endWikiClass(FilterEventParameters.EMPTY);

        // Tags object property
        // ListProperty only supports List type so we have to convert the Set
        proxyFilter.onWikiObjectProperty("tags", new ArrayList<>(categories), FilterEventParameters.EMPTY);

        proxyFilter.endWikiObject(REFERENCE_TAGCLASS, objectParameters);
    }

//    private String getPageContributor(XMLStreamReader xmlReader) throws XMLStreamException
//    {
//        String userName = null;
//
//        for (xmlReader.nextTag(); xmlReader.isStartElement(); xmlReader.nextTag()) {
//            String elementName = xmlReader.getLocalName();
//
//            switch (elementName) {
//                case TAG_PAGE_REVISION_CONTRIBUTOR_USERNAME:
//                    userName = xmlReader.getElementText();
//                    continue;
//
//                case TAG_PAGE_REVISION_CONTRIBUTOR_IP:
//                    if (!this.properties.isConvertToXWiki()) {
//                        userName = xmlReader.getElementText();
//                        continue;
//                    }
//            }
//
//            StAXUtils.skipElement(xmlReader);
//        }
//
//        return userName;
//    }
}
