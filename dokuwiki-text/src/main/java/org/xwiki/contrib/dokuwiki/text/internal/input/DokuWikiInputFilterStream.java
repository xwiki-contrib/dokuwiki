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
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
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

    private static final String KEY_CREATOR = "creator";

    private static final String KEY_LAST_CHANGE = "last_change";

    private static final String KEY_USER = "user";

    private static final String DOKUWIKI_START_PAGE = "start";

    private static final String XWIKI_START_PAGE = "WebHome";

    @Inject
    @Named("xwiki/2.1")
    private PrintRendererFactory xwiki21Factory;

    @Inject
    @Named(org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser.SYNTAX_STRING)
    private StreamParser dokuWikiParser;

    @Inject
    private Logger logger;

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
        Map<LocalDocumentReference, Path> pages;

        try {
            pages = readDocumentMap(new File(dokuwikiDataDirectory, KEY_PAGES_DIRECTORY).toPath());
        } catch (IOException e) {
            // This shouldn't happen as this is really just recursively scanning a directory and would thus indicate
            // a major problem.
            throw new FilterException("Failed to read page list", e);
        }

        // Sort pages by path. Store the result to work around limitations of the Java 8 Stream API regarding
        // checked exceptions from forEach.
        List<Map.Entry<LocalDocumentReference, Path>> sortedPages =
            pages.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());

        for (Map.Entry<LocalDocumentReference, Path> page : sortedPages) {
            try {
                readDocument(page.getKey(), page.getValue().toFile(), dokuwikiDataDirectory.getAbsolutePath(),
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

    private Map<LocalDocumentReference, Path> readDocumentMap(Path pagesDirectory)
        throws IOException
    {
        Map<LocalDocumentReference, Path> documentMap = new HashMap<>();

        try (Stream<Path> filesStream = Files.walk(pagesDirectory)) {
            filesStream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.endsWith(KEY_TEXT_FILE_FORMAT) && !fileName.startsWith(KEY_FULL_STOP);
                })
                .forEach(path -> {
                    String fileName = path.getFileName().toString();

                    // Extract all directories from the path excluding pagesDirectory.
                    List<String> parentPath = new ArrayList<>();
                    Path parent = path.getParent();
                    while (!parent.equals(pagesDirectory)) {
                        parentPath.add(parent.getFileName().toString());
                        parent = parent.getParent();
                    }

                    LocalDocumentReference documentReference = getDocumentReference(parentPath, fileName);

                    if (documentMap.containsKey(documentReference)) {
                        // Conflict resolution: We keep the one where the file is named "start". The
                        // other one is mapped to a terminal document unless it is in the root directory, in which case
                        // the only sensible option seems to be to make it a terminal document in the main space.
                        // Conflict resolution: keep
                        Path pathToFix;
                        if (fileName.equals(DOKUWIKI_START_PAGE + KEY_TEXT_FILE_FORMAT)) {
                            pathToFix = documentMap.get(documentReference);
                            documentMap.put(documentReference, path);
                        } else {
                            pathToFix = path;
                        }

                        if (documentReference.getParent().getParent() == null) {
                            documentReference = new LocalDocumentReference(Collections.singletonList(KEY_MAIN_SPACE),
                                documentReference.getParent().getName());
                        } else {
                            documentReference = new LocalDocumentReference(documentReference.getParent().getName(),
                                documentReference.getParent().getParent());
                        }

                        documentMap.put(documentReference, pathToFix);
                    } else {
                        documentMap.put(documentReference, path);
                    }
                });
        }

        return documentMap;
    }

    private static LocalDocumentReference getDocumentReference(List<String> parentPath, String fileName)
    {
        // Extract the document reference from the file path. Convert directory names and the file name
        // into spaces. If the file name is "start", then the file is the home page of the space.
        // Otherwise, create a space with the file name as the space name to ensure that only
        // non-terminal documents are created.
        String pageName = fileName.substring(0, fileName.length() - KEY_TEXT_FILE_FORMAT.length());
        LocalDocumentReference documentReference;
        if (pageName.equals(DOKUWIKI_START_PAGE)) {
            if (parentPath.isEmpty()) {
                documentReference = new LocalDocumentReference(Collections.singletonList(KEY_MAIN_SPACE),
                    XWIKI_START_PAGE);
            } else {
                documentReference =
                    new LocalDocumentReference(parentPath, XWIKI_START_PAGE);
            }
        } else {
            List<String> spacePath = new ArrayList<>(parentPath);
            spacePath.add(pageName);
            documentReference = new LocalDocumentReference(spacePath, XWIKI_START_PAGE);
        }
        return documentReference;
    }

    private void readDocument(LocalDocumentReference documentReference, File file, String dokuwikiDataDirectory,
        DokuWikiFilter proxyFilter) throws FilterException, IOException
    {
        if (this.properties.isVerbose()) {
            this.logger.info("Reading file [{}]", file.getAbsolutePath());
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

        String fileMetadataPath = file.getAbsolutePath()
            .replace(dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_PAGES_DIRECTORY,
                dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + "meta")
            .replace(KEY_TEXT_FILE_FORMAT, ".meta");
        // FilterEventParameters documentParameters = new FilterEventParameters();
        // documentParameters.put(WikiDocumentFilter.PARAMETER_LOCALE, Locale.ROOT);

        // wiki document
        FilterEventParameters documentLocaleParameters = new FilterEventParameters();

        File fileMetadata = new File(fileMetadataPath);
        if (fileMetadata.exists() && !fileMetadata.isDirectory()) {
            String metadataFileContents = FileUtils.readFileToString(fileMetadata, StandardCharsets.UTF_8);
            MixedArray documentMetadata = Pherialize.unserialize(metadataFileContents).toArray();
            readDocumentParametersFromMetadata(documentMetadata, documentLocaleParameters);

            // Wiki document revision

            if ((documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).containsKey(KEY_CREATED)
                && documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).containsKey(KEY_MODIFIED))
                && documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE)
                    .getLong(KEY_CREATED) < documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE)
                        .getLong(KEY_MODIFIED)) {
                // Wiki Document Locale
                proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
                // read revisions
                readPageRevision(file, dokuwikiDataDirectory, proxyFilter);
            } else {
                documentLocaleParameters =
                    readDocumentContent(file, documentLocaleParameters, dokuwikiDataDirectory, proxyFilter);
            }
        } else {
            this.logger.warn("File [{}] not found (Some datafile's properties (eg. filesize, "
                + "last modified date) are not imported. Details can be found on "
                + "https://www.dokuwiki.org/devel:metadata)", fileMetadata);
            documentLocaleParameters =
                readDocumentContent(file, documentLocaleParameters, dokuwikiDataDirectory, proxyFilter);
        }

        proxyFilter.endWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
        proxyFilter.endWikiDocument(documentReference.getName(), FilterEventParameters.EMPTY);

        // Close spaces in reverse order.
        Collections.reverse(parentReferences);
        for (EntityReference parent : parentReferences) {
            proxyFilter.endWikiSpace(parent.getName(), FilterEventParameters.EMPTY);
        }
    }

    private FilterEventParameters readDocumentContent(File file, FilterEventParameters documentLocaleParameters,
        String dokuwikiDataDirectory, DokuWikiFilter proxyFilter)
    {
        try {
            String pageContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            String convertedContent = parseContent(pageContents);
            documentLocaleParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertedContent);
            // Wiki Document Locale
            proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
            readAttachments(pageContents, file, dokuwikiDataDirectory, proxyFilter);

            return documentLocaleParameters;
        } catch (Exception e) {
            this.logger.error("Failed to parse DockuWiki file [{}]", file, e);
        }

        return documentLocaleParameters;
    }

    private void readPageRevision(File file, String dokuwikiDataDirectory, DokuWikiFilter proxyFilter)
    {
        // check revision exists, check the attic, parse attic files.
        String fileName = file.getName().replace(KEY_TEXT_FILE_FORMAT, "");
        String fileRevisionDirectory = file.getAbsolutePath()
            .replace(dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_PAGES_DIRECTORY,
                dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_ATTIC_FOLDER)
            .replace(System.getProperty(KEY_FILE_SEPERATOR) + file.getName(), "");

        File atticDirectoryForFile = new File(fileRevisionDirectory);
        File[] revisionFiles = atticDirectoryForFile.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.startsWith(fileName);
            }
        });
        if (revisionFiles != null) {
            // Maintain the order of files
            Arrays.sort(revisionFiles);
            for (File revisionFile : revisionFiles) {
                String revision = revisionFile.getName().replace(fileName + KEY_FULL_STOP, "").replace(".txt.gz", "");
                try {
                    String documentContent = extractGZip(revisionFile);
                    String convertedContent = parseContent(documentContent);
                    FilterEventParameters revisionParameters = new FilterEventParameters();
                    revisionParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertedContent);
                    proxyFilter.beginWikiDocumentRevision(revision, revisionParameters);
                    readAttachments(documentContent, revisionFile, dokuwikiDataDirectory, proxyFilter);
                    proxyFilter.endWikiDocumentRevision(revision, revisionParameters);
                } catch (Exception e) {
                    this.logger.error("Failed to parse file [{}]", revisionFile.getAbsolutePath(), e);
                }
            }
        }
    }

    private String parseContent(String pageContents)
    {
        String content = "";
        try {
            // parse pageContent
            DefaultWikiPrinter printer = new DefaultWikiPrinter();
            PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
            dokuWikiParser.parse(new StringReader(pageContents), renderer);
            content = renderer.getPrinter().toString();
        } catch (ParseException e) {
            this.logger.error("Failed to parse page content", e);
        }
        return content;
    }

    private void readAttachments(String content, File document, String dokuwikiDataDirectory,
        DokuWikiFilter proxyFilter)
    {
        String mediaNameSpaceDirectoryPath = document.getAbsolutePath()
            .replace(dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_PAGES_DIRECTORY,
                dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_MEDIA_FOLDER)
            .replace(dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_ATTIC_FOLDER,
                dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + KEY_MEDIA_FOLDER)
            .replace(document.getName(), "");

        File mediaSpace = new File(mediaNameSpaceDirectoryPath);
        if (mediaSpace.exists() && mediaSpace.isDirectory()) {
            File[] mediaFiles = mediaSpace.listFiles();
            if (mediaFiles != null) {
                for (File attachment : mediaFiles) {
                    String attachmentName = attachment.getName();
                    if (!attachmentName.startsWith(KEY_FULL_STOP) && !attachmentName.startsWith("_")
                        && content.contains(attachmentName) && !attachment.isDirectory()) {
                        try {
                            FileInputStream attachmentStream = FileUtils.openInputStream(attachment);
                            proxyFilter.onWikiAttachment(attachment.getName(), attachmentStream,
                                FileUtils.sizeOf(attachment), FilterEventParameters.EMPTY);
                            attachmentStream.close();
                        } catch (FilterException | IOException e) {
                            this.logger.error("Failed to process attachment", e);
                        }
                    }
                }
            }
        }
    }

    private void readDocumentParametersFromMetadata(MixedArray documentMetadata,
        FilterEventParameters documentParameters)
    {
        if (documentMetadata.getArray(KEY_PERSISTENT).containsKey(KEY_CREATOR)
            && !documentMetadata.getArray(KEY_PERSISTENT).get(KEY_CREATOR).equals("")) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_AUTHOR,
                documentMetadata.getArray(KEY_PERSISTENT).getString(KEY_CREATOR));
        }
        documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_DATE,
            new Date(1000 * documentMetadata.getArray(KEY_PERSISTENT).getArray(KEY_DATE).getLong(KEY_CREATED)));
        if (documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).containsKey(KEY_MODIFIED)
            && !documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getString(KEY_MODIFIED).equals("")) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_LASTREVISION,
                documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getString(KEY_MODIFIED));
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_DATE,
                new Date(1000 * documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getLong(KEY_MODIFIED)));
        }
        if (documentMetadata.getArray(KEY_CURRENT).containsKey(KEY_LAST_CHANGE)
            && !documentMetadata.getArray(KEY_CURRENT).getString(KEY_USER).equals("")) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_AUTHOR,
                documentMetadata.getArray(KEY_CURRENT).getArray(KEY_LAST_CHANGE).getString(KEY_USER));
        }
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

    private String extractGZip(File file) throws IOException
    {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(file));
        String content = IOUtils.toString(gzipInputStream, StandardCharsets.UTF_8);
        gzipInputStream.close();
        return content;
    }

    @Override
    public void close() throws IOException
    {
        this.properties.getSource().close();
    }
}
