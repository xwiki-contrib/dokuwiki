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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties;
import org.xwiki.contrib.dokuwiki.text.internal.DokuWikiFilter;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.event.model.WikiDocumentFilter;
import org.xwiki.filter.event.user.UserFilter;
import org.xwiki.filter.input.AbstractBeanInputFilterStream;
import org.xwiki.filter.input.FileInputSource;
import org.xwiki.filter.input.InputSource;
import org.xwiki.filter.input.InputStreamInputSource;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;

import de.ailis.pherialize.MixedArray;
import de.ailis.pherialize.Pherialize;

/**
 * @version $Id: 41df1dab66b03111214dbec56fee8dbd44747638 $
 */
@Component
@Named(DokuWikiInputProperties.FILTER_STREAM_TYPE_STRING)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiInputFilterStream extends AbstractBeanInputFilterStream<DokuWikiInputProperties, DokuWikiFilter>
{
    private static final String KEY_PAGES_DIRECTORY = "pages";

    private static final String KEY_MAIN_SPACE = "Main";

    private static final String KEY_TEXT_FILE_FORMAT = ".txt";

    private static final String KEY_DOKUWIKI = "dokuwiki";

    private static final String KEY_FILE_SEPERATOR = "file.separator";

    private static final String KEY_FULL_STOP = ".";

    private static final String KEY_CURRENT = "current";

    private static final String KEY_DATE = "date";

    private static final String KEY_CREATED = "created";

    private static final String KEY_MODIFIED = "modified";

    private static final String KEY_ATTIC_FOLDER = "attic";

    private static final String KEY_MEDIA_FOLDER = "media";

    private static final String KEY_PERSISTENT = "persistent";

    private static final String KEY_LAST_CHANGE = "last_change";

    private static final String KEY_USER = "user";

    private static final String DOKUWIKI_START_PAGE = "start";

    @Inject
    @Named("xwiki/2.1")
    private PrintRendererFactory xwiki21Factory;

    @Inject
    @Named(org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser.SYNTAX_STRING)
    private StreamParser dokuWikiParser;

    @Inject
    private Provider<DokuWikiConverterListener> dokuWikiConverterListenerProvider;

    @Inject
    private DokuWikiReferenceConverter dokuWikiReferenceConverter;

    @Inject
    private Logger logger;

    private static class DokuWikiPageItem
    {
        private final String dokuwikiReference;

        private final List<Path> attachments;

        private final Path pageFile;

        DokuWikiPageItem(String dokuwikiReference, Path pageFile)
        {
            this.dokuwikiReference = dokuwikiReference;
            this.attachments = new ArrayList<>();
            this.pageFile = pageFile;
        }

        public String getDokuWikiReference()
        {
            return dokuwikiReference;
        }

        public List<Path> getAttachments()
        {
            return attachments;
        }

        public Path getPageFile()
        {
            return pageFile;
        }
    }

    @Override
    protected void read(Object filter, DokuWikiFilter proxyFilter) throws FilterException
    {
        InputSource inputSource = this.properties.getSource();
        if (inputSource instanceof FileInputSource) {
            File f = ((FileInputSource) inputSource).getFile();
            if (f.exists()) {
                if (f.isDirectory()) {
                    File dokuwikiDataDirectory = new File(f, "data");
                    readUsers(new File(f, "conf" + System.getProperty(KEY_FILE_SEPERATOR) + "users.auth.php"),
                        proxyFilter);

                    readAllDocuments(proxyFilter, dokuwikiDataDirectory);
                } else {
                    read((InputStreamInputSource) inputSource, filter, proxyFilter);
                }
            } else {
                this.logger.error("File doesn't exists.");
            }
        } else if (inputSource instanceof InputStreamInputSource) {
            read((InputStreamInputSource) inputSource, filter, proxyFilter);
        } else {
            throw new FilterException("Unsupported input source [" + inputSource.getClass() + "]");
        }
    }

    private void read(InputStreamInputSource inputSource, Object filter, DokuWikiFilter proxyFilter)
        throws FilterException
    {
        try (BufferedInputStream inputStream = new BufferedInputStream(inputSource.getInputStream())) {
            try {
                // Try compressed archive
                ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(
                    new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(inputStream)));
                readDataStream(archiveInputStream, filter, proxyFilter);
            } catch (CompressorException e1) {
                try {
                    // Try archive
                    ArchiveInputStream archiveInputStream =
                        new ArchiveStreamFactory().createArchiveInputStream(inputStream);
                    readDataStream(archiveInputStream, filter, proxyFilter);
                } catch (ArchiveException e2) {
                    this.logger.error("Failed to open the input as compressed archive", e1);
                    this.logger.error("Failed to read/unarchive or unknown format from stream input", e2);
                }
            } catch (ArchiveException e) {
                this.logger.error("Failed to read/unarchive from stream input", e);
            }
        } catch (IOException e) {
            this.logger.error("Failed to open source", e);
        }
    }

    private void readDataStream(ArchiveInputStream archiveInputStream, Object filter, DokuWikiFilter proxyFilter)
        throws FilterException
    {

        ArchiveEntry archiveEntry = null;
        // create dokuwiki temporary directory
        File dokuwikiDirectory = null;
        try {
            archiveEntry = archiveInputStream.getNextEntry();
            dokuwikiDirectory = File.createTempFile("dokuwiki", "");
        } catch (IOException e) {
            this.logger.error("Couldn't create temporary folder for dokuwiki", e);
        }
        dokuwikiDirectory.delete();
        dokuwikiDirectory.mkdir();

        // Dokuwiki's data directory
        File dokuwikiDataDirectory = new File(dokuwikiDirectory, "data");

        while (archiveEntry != null) {
            /*
             * All filters parsing any file will make the respecting entry file blank, the file is saved in dokuwiki
             * temporary folder now.
             */
            saveEntryToDisk(archiveInputStream, archiveEntry, dokuwikiDirectory);

            // get next file from archive stream
            try {
                archiveEntry = archiveInputStream.getNextEntry();
            } catch (IOException e) {
                this.logger.error("couldn't read next entry", e);
            }
        }

        // readUsers
        readUsers(new File(dokuwikiDirectory, "conf" + System.getProperty(KEY_FILE_SEPERATOR) + "users.auth.php"),
            proxyFilter);

        readAllDocuments(proxyFilter, dokuwikiDataDirectory);

        try {
            FileUtils.deleteDirectory(dokuwikiDirectory);
        } catch (IOException e) {
            this.logger.error("Could not delete dokuwiki folder after completion", e);
        }
    }

    private void readAllDocuments(DokuWikiFilter proxyFilter, File dokuwikiDataDirectory) throws FilterException
    {
        Map<LocalDocumentReference, DokuWikiPageItem> pages;

        try {
            pages = readDocumentMap(dokuwikiDataDirectory.toPath().resolve(KEY_PAGES_DIRECTORY));
        } catch (IOException e) {
            // This shouldn't happen as this is really just recursively scanning a directory and would thus indicate
            // a major problem.
            throw new FilterException("Failed to read page list", e);
        }

        Map<LocalDocumentReference, DokuWikiPageItem> attachments =
            readAttachmentMap(dokuwikiDataDirectory.toPath().resolve(KEY_MEDIA_FOLDER));
        attachments.forEach((page, attachmentPageItem) ->
            pages.compute(page, (p, documentPageItem) -> {
                if (documentPageItem == null) {
                    return attachmentPageItem;
                } else {
                    documentPageItem.getAttachments().addAll(attachmentPageItem.getAttachments());
                    return documentPageItem;
                }
            })
        );

        // Sort pages by path. Store the result to work around limitations of the Java 8 Stream API regarding
        // checked exceptions from forEach.
        List<Map.Entry<LocalDocumentReference, DokuWikiPageItem>> sortedPages =
            pages.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().getDokuWikiReference()))
                .collect(Collectors.toList());

        for (Map.Entry<LocalDocumentReference, DokuWikiPageItem> page : sortedPages) {
            try {
                readDocument(page.getKey(), page.getValue(), dokuwikiDataDirectory.toPath(),
                    proxyFilter);
            } catch (IOException e) {
                // Don't fail the whole import if a single page fails.
                this.logger.error("Failed to read page", e);
            }
        }
    }

    private void readUsers(File userInformation, DokuWikiFilter proxyFilter) throws FilterException
    {
        List<String> lines = null;
        try {
            lines = FileUtils.readLines(userInformation, StandardCharsets.UTF_8);
        } catch (IOException e) {
            this.logger.warn("Couldn't read user information", e);
        }

        if (lines != null) {
            for (String line : lines) {
                if (!(line.length() == 0 || line.startsWith("#"))) {
                    String[] parameters = line.split(":");
                    FilterEventParameters userParameters = new FilterEventParameters();
                    userParameters.put(UserFilter.PARAMETER_FIRSTNAME, parameters[2].split(" ")[0]);
                    // User may not have a second name in Dokuwiki
                    if (parameters[2].split(" ").length > 1) {
                        userParameters.put(UserFilter.PARAMETER_LASTNAME, parameters[2].split(" ")[1]);
                    }
                    userParameters.put(UserFilter.PARAMETER_EMAIL, parameters[3]);
                    proxyFilter.beginUser(parameters[0], userParameters);
                    proxyFilter.endUser(parameters[0], userParameters);
                }
            }
        }
    }

    private Map<LocalDocumentReference, DokuWikiPageItem> readDocumentMap(Path pagesDirectory) throws IOException
    {
        Map<LocalDocumentReference, DokuWikiPageItem> documentMap = new HashMap<>();

        try (Stream<Path> filesStream = Files.walk(pagesDirectory)) {
            filesStream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.endsWith(KEY_TEXT_FILE_FORMAT) && !fileName.startsWith(KEY_FULL_STOP);
                })
                .forEach(path -> {
                    String fileName = path.getFileName().toString();

                    String dokuwikiReference = dokuWikiReferenceConverter.getDokuWikiReference(path, pagesDirectory);
                    LocalDocumentReference documentReference =
                        this.dokuWikiReferenceConverter.getDocumentReference(dokuwikiReference);

                    DokuWikiPageItem pageItem = new DokuWikiPageItem(dokuwikiReference, path);

                    if (documentMap.containsKey(documentReference)) {
                        // Conflict resolution: We keep the one where the file is named "start". The
                        // other one is mapped to a terminal document unless it is in the root directory, in which case
                        // the only sensible option seems to be to make it a terminal document in the main space.
                        DokuWikiPageItem itemToFix;
                        if (fileName.equals(DOKUWIKI_START_PAGE + KEY_TEXT_FILE_FORMAT)) {
                            itemToFix = documentMap.get(documentReference);
                            documentMap.put(documentReference, pageItem);
                        } else {
                            itemToFix = pageItem;
                        }

                        if (documentReference.getParent().getParent() == null) {
                            documentReference = new LocalDocumentReference(Collections.singletonList(KEY_MAIN_SPACE),
                                documentReference.getParent().getName());
                        } else {
                            documentReference = new LocalDocumentReference(documentReference.getParent().getName(),
                                documentReference.getParent().getParent());
                        }

                        documentMap.put(documentReference, itemToFix);
                    } else {
                        documentMap.put(documentReference, pageItem);
                    }
                });
        }

        return documentMap;
    }

    private Map<LocalDocumentReference, DokuWikiPageItem> readAttachmentMap(Path mediaDirectory)
    {
        Map<LocalDocumentReference, DokuWikiPageItem> attachmentMap = new HashMap<>();

        try (Stream<Path> filesStream = Files.walk(mediaDirectory)) {
            filesStream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String attachmentName = path.getFileName().toString();
                    return !attachmentName.startsWith(KEY_FULL_STOP) && !attachmentName.startsWith("_");
                })
                .forEach(path -> {
                    Path fakePageFile = path.getParent().resolve(DOKUWIKI_START_PAGE + KEY_TEXT_FILE_FORMAT);
                    String dokuwikiReference =
                        this.dokuWikiReferenceConverter.getDokuWikiReference(fakePageFile, mediaDirectory);
                    LocalDocumentReference documentReference =
                        this.dokuWikiReferenceConverter.getDocumentReference(dokuwikiReference);

                    List<Path> attachments = attachmentMap.computeIfAbsent(documentReference, k ->
                        new DokuWikiPageItem(dokuwikiReference, fakePageFile)).getAttachments();
                    attachments.add(path);
                });
        } catch (IOException e) {
            this.logger.error("Failed to list attachments", e);
        }

        return attachmentMap;
    }


    private void readDocument(LocalDocumentReference documentReference, DokuWikiPageItem pageItem,
        Path dokuwikiDataDirectory, DokuWikiFilter proxyFilter) throws FilterException, IOException
    {
        Path file = pageItem.getPageFile();

        if (this.properties.isVerbose()) {
            this.logger.info("Reading file [{}]", file.toAbsolutePath());
        }

        // Begin the space and document.
        ArrayList<EntityReference> parentReferences = new ArrayList<>();
        EntityReference parentReference = documentReference.getParent();
        while (parentReference != null) {
            parentReferences.add(parentReference);
            parentReference = parentReference.getParent();
        }

        // Open spaces from root to the current document.
        Collections.reverse(parentReferences);
        for (EntityReference parent : parentReferences) {
            proxyFilter.beginWikiSpace(parent.getName(), FilterEventParameters.EMPTY);
        }

        proxyFilter.beginWikiDocument(documentReference.getName(), FilterEventParameters.EMPTY);

        // Extract the filename without the file extension.
        String fileNameWithoutExtension = getFileNameWithoutTxtExtension(file);

        // Extract path below the data directory and replace the pages directory with the meta directory.
        Path metaSubDirectory = getMatchingDirectory(file.getParent(), dokuwikiDataDirectory, "meta");
        Path metaFile = metaSubDirectory.resolve(fileNameWithoutExtension + ".meta");

        // wiki document
        FilterEventParameters documentLocaleParameters = new FilterEventParameters();

        if (Files.isRegularFile(metaFile)) {
            try {
                String metadataFileContents = new String(Files.readAllBytes(metaFile), StandardCharsets.UTF_8);
                MixedArray documentMetadata = Pherialize.unserialize(metadataFileContents).toArray();
                readDocumentParametersFromMetadata(documentMetadata, documentLocaleParameters);

                // Wiki document revision
                Long created = getLongMetadata(documentMetadata, KEY_DATE, KEY_CREATED);
                Long modified = getLongMetadata(documentMetadata, KEY_DATE, KEY_MODIFIED);
                if (created != null && modified != null && created < modified) {
                    // Wiki Document Locale
                    proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
                    // read revisions
                    readPageRevision(pageItem, dokuwikiDataDirectory, proxyFilter);
                } else {
                    documentLocaleParameters =
                        readDocumentContent(pageItem, documentLocaleParameters, proxyFilter);
                }
            } catch (Exception e) {
                this.logger.warn(
                    "Failed to parse DokuWiki page with metadata file [{}], ignoring metadata. Root cause: {}.",
                    metaFile, ExceptionUtils.getRootCauseMessage(e));
                documentLocaleParameters =
                    readDocumentContent(pageItem, documentLocaleParameters, proxyFilter);
            }
        } else {
            this.logger.warn("File [{}] not found (Some datafile's properties (eg. filesize, "
                + "last modified date) are not imported. Details can be found on "
                + "https://www.dokuwiki.org/devel:metadata)", metaFile);
            documentLocaleParameters =
                readDocumentContent(pageItem, documentLocaleParameters, proxyFilter);
        }

        proxyFilter.endWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
        proxyFilter.endWikiDocument(documentReference.getName(), FilterEventParameters.EMPTY);

        // Close spaces in reverse order.
        Collections.reverse(parentReferences);
        for (EntityReference parent : parentReferences) {
            proxyFilter.endWikiSpace(parent.getName(), FilterEventParameters.EMPTY);
        }
    }

    private FilterEventParameters readDocumentContent(DokuWikiPageItem pageItem,
        FilterEventParameters documentLocaleParameters, DokuWikiFilter proxyFilter)
    {
        Path file = pageItem.getPageFile();

        try {
            // The page might not actually exist if it is just created for storing attachments.
            if (Files.isRegularFile(file)) {
                String pageContents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

                String convertedContent = parseContent(pageContents, pageItem.getDokuWikiReference());
                documentLocaleParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertedContent);
            }
            // Wiki Document Locale
            proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
            readAttachments(pageItem, proxyFilter);

            return documentLocaleParameters;
        } catch (Exception e) {
            this.logger.error("Failed to parse DokuWiki file [{}]", file, e);
        }

        return documentLocaleParameters;
    }

    private static String getFileNameWithoutTxtExtension(Path file)
    {
        String fileName = file.getFileName().toString();
        return fileName.substring(0, fileName.length() - KEY_TEXT_FILE_FORMAT.length());
    }

    private void readPageRevision(DokuWikiPageItem pageItem, Path dokuwikiDataDirectory, DokuWikiFilter proxyFilter)
    {
        Path file = pageItem.getPageFile();

        // check revision exists, check the attic, parse attic files.
        Path atticSubDirectory = getMatchingDirectory(file.getParent(), dokuwikiDataDirectory, KEY_ATTIC_FOLDER);

        String fileNameWithoutExtension = getFileNameWithoutTxtExtension(file);

        if (Files.isDirectory(atticSubDirectory)) {
            try (Stream<Path> stream = Files.list(atticSubDirectory)) {
                stream.filter(p -> p.getFileName().toString().startsWith(fileNameWithoutExtension))
                    .sorted(Comparator.comparing(p -> extractRevision(fileNameWithoutExtension, p)))
                    .forEach(p -> {
                        try {
                            long revision = extractRevision(fileNameWithoutExtension, p);
                            String documentContent = extractGZip(p);
                            String convertedContent = parseContent(documentContent, pageItem.getDokuWikiReference());
                            FilterEventParameters revisionParameters = new FilterEventParameters();
                            revisionParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertedContent);
                            proxyFilter.beginWikiDocumentRevision(String.valueOf(revision), revisionParameters);
                            readAttachments(pageItem, proxyFilter);
                            proxyFilter.endWikiDocumentRevision(String.valueOf(revision), FilterEventParameters.EMPTY);
                        } catch (Exception e) {
                            this.logger.error("Failed to parse file [{}]", p, e);
                        }
                    });
            } catch (IOException e) {
                this.logger.error("Failed to read attic directory [{}]", atticSubDirectory, e);
            }
        }
    }

    private static long extractRevision(String fileNameWithoutExtension, Path p)
    {
        String revision = p.getFileName().toString().replace(fileNameWithoutExtension + KEY_FULL_STOP, "")
            .replace(".txt.gz", "");
        return Long.parseLong(revision);
    }

    private String parseContent(String pageContents, String dokuwikiReference)
    {
        String content = "";
        try {
            // parse pageContent
            DefaultWikiPrinter printer = new DefaultWikiPrinter();
            PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
            DokuWikiConverterListener listener = this.dokuWikiConverterListenerProvider.get();
            listener.setDokuWikiReference(dokuwikiReference);
            listener.setWrappedListener(renderer);
            dokuWikiParser.parse(new StringReader(pageContents), listener);
            content = renderer.getPrinter().toString();
        } catch (ParseException e) {
            this.logger.error("Failed to parse page content", e);
        }
        return content;
    }

    private void readAttachments(DokuWikiPageItem pageItem, DokuWikiFilter proxyFilter)
    {
        for (Path path : pageItem.getAttachments()) {
            String attachmentName = path.getFileName().toString();
            try (InputStream attachmentStream = Files.newInputStream(path)) {
                proxyFilter.onWikiAttachment(attachmentName, attachmentStream,
                    Files.size(path), FilterEventParameters.EMPTY);
            } catch (IOException | FilterException e) {
                this.logger.error("Failed to process attachment [{}]", path, e);
            }
        }
    }

    /**
     * For a given pages, attic, ... subdirectory return the same subdirectory in another base directory (like meta).
     *
     * @param originDirectory the original directory to convert
     * @param dokuWikiDataDirectory the data directory, must be an ancestor of the originDirectory
     * @param directoryName the new base directory name
     * @return the subdirectory in the wanted base directory
     */
    private Path getMatchingDirectory(Path originDirectory, Path dokuWikiDataDirectory, String directoryName)
    {
        Path relativePath = dokuWikiDataDirectory.relativize(originDirectory);
        Path result;
        if (relativePath.getNameCount() == 1) {
            result = dokuWikiDataDirectory.resolve(directoryName);
        } else {
            Path relativePathWithoutPagesDirectory = relativePath.subpath(1, relativePath.getNameCount());
            result = dokuWikiDataDirectory.resolve(directoryName).resolve(relativePathWithoutPagesDirectory);
        }
        return result;
    }

    private void readDocumentParametersFromMetadata(MixedArray documentMetadata,
        FilterEventParameters documentParameters)
    {
        // Save the creator, only available since 2011-05-25 (note that the creator metadata in DokuWiki is the display
        // name, not the actual username, see https://bugs.dokuwiki.org/1397.html).
        String creator = getStringMetadata(documentMetadata, KEY_USER);
        if (creator != null) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_AUTHOR, creator);
        }

        Long dateCreated = getLongMetadata(documentMetadata, KEY_DATE, KEY_CREATED);
        // Save the creation date.
        if (dateCreated != null) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_DATE, new Date(1000 * dateCreated));
        }

        Long dateModified = getLongMetadata(documentMetadata, KEY_DATE, KEY_MODIFIED);
        // Save the last revision date.
        if (dateModified != null) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_LASTREVISION,
                getStringMetadata(documentMetadata, KEY_DATE, KEY_MODIFIED));
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_DATE, new Date(1000 * dateModified));
        }

        // Save the last revision author.
        String lastRevisionAuthor = getStringMetadata(documentMetadata, KEY_LAST_CHANGE, KEY_USER);
        if (lastRevisionAuthor != null) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_AUTHOR, lastRevisionAuthor);
        }
    }

    private static <T> T getMetadata(MixedArray documentMetadata, String arrayName, String key,
        Function<MixedArray, T> valueExtractor)
    {
        // Try both current and persistent metadata as metadata in DokuWiki is saved inconsistently.
        // In old versions, it seems that sometimes only persistent metadata is present and correct, while in more
        // recent versions it seems that some values only exist in the current metadata for external edits.
        for (String persistentCurrent : Arrays.asList(KEY_CURRENT, KEY_PERSISTENT)) {
            MixedArray metadataArray;
            // Check if we have persistent and current metadata (after 2006-11-06).
            if (documentMetadata.containsKey(persistentCurrent)) {
                metadataArray = documentMetadata.getArray(persistentCurrent);
            } else {
                metadataArray = documentMetadata;
            }
            MixedArray innerMetadata;
            if (arrayName == null) {
                innerMetadata = metadataArray;
            } else if (metadataArray.containsKey(arrayName)) {
                innerMetadata = metadataArray.getArray(arrayName);
            } else {
                innerMetadata = null;
            }
            if (innerMetadata != null && innerMetadata.containsKey(key)
                && StringUtils.isNotBlank(innerMetadata.getString(key))) {
                return valueExtractor.apply(innerMetadata);
            }
        }
        return null;
    }

    private static String getStringMetadata(MixedArray documentMetadata, String arrayName, String key)
    {
        return getMetadata(documentMetadata, arrayName, key, array -> array.getString(key));
    }

    private static Long getLongMetadata(MixedArray documentMetadata, String arrayName, String key)
    {
        return getMetadata(documentMetadata, arrayName, key, array -> array.getLong(key));
    }

    private static String getStringMetadata(MixedArray documentMetadata, String key)
    {
        return getStringMetadata(documentMetadata, null, key);
    }

    private void saveEntryToDisk(ArchiveInputStream archiveInputStream, ArchiveEntry archiveEntry, File folderToSave)
    {
        String entryName = archiveEntry.getName();
        if (entryName.startsWith(KEY_DOKUWIKI)) {
            entryName = entryName.replaceFirst(KEY_DOKUWIKI + System.getProperty(KEY_FILE_SEPERATOR), "");
        }
        if (!archiveEntry.isDirectory()) {
            try {
                /**
                 * FileUtils.copyToFile closes the input stream, so, Proxy stream prevents the archiveInputStream from
                 * being closed. This is a workaround for a bug at commons-io side.
                 * (https://issues.apache.org/jira/browse/IO-554). If fixed on commons-io side, pass archiveInputStream
                 * to FileUtils.copyToFile() directly.
                 */
                CloseShieldInputStream proxy = new CloseShieldInputStream(archiveInputStream);
                FileUtils.copyToFile(proxy, new File(folderToSave, entryName));
            } catch (IOException e) {
                this.logger.error("failed to write stream to file", e);
            }
        }
    }

    private String extractGZip(Path file) throws IOException
    {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(Files.newInputStream(file))) {
            return IOUtils.toString(gzipInputStream, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() throws IOException
    {
        this.properties.getSource().close();
    }
}
