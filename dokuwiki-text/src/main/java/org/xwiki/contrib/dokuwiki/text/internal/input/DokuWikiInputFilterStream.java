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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
            if (f.exists() && f.isDirectory()) {
                File dokuwikiDataDirectory = new File(f, "data");
                readUsers(new File(f, "conf" + System.getProperty(KEY_FILE_SEPERATOR)
                        + "users.auth.php"), proxyFilter);

                //recursively parse documents
                proxyFilter.beginWikiSpace(KEY_MAIN_SPACE, FilterEventParameters.EMPTY);
                try {
                    readDocument(new File(dokuwikiDataDirectory, KEY_PAGES_DIRECTORY),
                            dokuwikiDataDirectory.getAbsolutePath(), proxyFilter);
                } catch (IOException e) {
                    this.logger.error("couldn't read document", e);
                }
                proxyFilter.endWikiSpace(KEY_MAIN_SPACE, FilterEventParameters.EMPTY);
            } else {
                try {
                    CompressorInputStream input = new CompressorStreamFactory()
                            .createCompressorInputStream(new BufferedInputStream(
                                    ((InputStreamInputSource) inputSource).getInputStream()));
                    ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                            .createArchiveInputStream(new BufferedInputStream(input));
                    readDataStream(archiveInputStream, filter, proxyFilter);
                    input.close();
                } catch (Exception e1) {
                    try {
                        FileInputSource fileInputSource = (FileInputSource) this.properties.getSource();
                        FileInputStream fileInputStream = FileUtils.openInputStream(fileInputSource.getFile());
                        ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                                .createArchiveInputStream(new BufferedInputStream(fileInputStream));
                        readDataStream(archiveInputStream, filter, proxyFilter);
                        fileInputStream.close();
                        archiveInputStream.close();
                    } catch (IOException | ArchiveException e2) {
                        this.logger.error("Failed to read/unarchive or unknown format from file input type", e1, e2);
                    }
                }
            }
        } else if (inputSource instanceof InputStreamInputSource) {
            try {
                CompressorInputStream input = new CompressorStreamFactory()
                        .createCompressorInputStream(((InputStreamInputSource) inputSource).getInputStream());
                ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                        .createArchiveInputStream(new BufferedInputStream(input));
                readDataStream(archiveInputStream, filter, proxyFilter);
                input.close();
            } catch (Exception e1) {
                try {
                    ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                            .createArchiveInputStream(
                                    new BufferedInputStream(((InputStreamInputSource) inputSource)
                                            .getInputStream()));
                    readDataStream(archiveInputStream, filter, proxyFilter);
                    archiveInputStream.close();
                } catch (IOException | ArchiveException e2) {
                    this.logger.error("Failed to read/unarchive or unknown format from stream input", e1, e2);
                }
            }
        } else {
            throw new FilterException("Unsupported input source [" + inputSource.getClass() + "]");
        }
    }

    private void readDataStream(ArchiveInputStream archiveInputStream,
            Object filter, DokuWikiFilter proxyFilter)
            throws FilterException
    {

        ArchiveEntry archiveEntry = null;
        //create dokuwiki temporary  directory
        File dokuwikiDirectory = null;
        try {
            archiveEntry = archiveInputStream.getNextEntry();
            dokuwikiDirectory = File.createTempFile("dokuwiki", "");
        } catch (IOException e) {
            this.logger.error("Couldn't create temporary folder for dokuwiki", e);
        }
        dokuwikiDirectory.delete();
        dokuwikiDirectory.mkdir();

        //Dokuwiki's data directory
        File dokuwikiDataDirectory = new File(dokuwikiDirectory, "data");

        while (archiveEntry != null) {
            /*
            All filters parsing any file will make the respecting entry file blank,
            the file is saved in dokuwiki temporary folder now.
            */
            saveEntryToDisk(archiveInputStream, archiveEntry, dokuwikiDirectory);

            //get next file from archive stream
            try {
                archiveEntry = archiveInputStream.getNextEntry();
            } catch (IOException e) {
                this.logger.error("couldn't read next entry", e);
            }
        }

        //readUsers
        readUsers(new File(dokuwikiDirectory, "conf"
                        + System.getProperty(KEY_FILE_SEPERATOR)
                        + "users.auth.php"),
                proxyFilter);

        //recursively parse documents
        proxyFilter.beginWikiSpace(KEY_MAIN_SPACE, FilterEventParameters.EMPTY);
        try {
            readDocument(new File(dokuwikiDataDirectory, KEY_PAGES_DIRECTORY),
                    dokuwikiDataDirectory.getAbsolutePath(), proxyFilter);
        } catch (IOException e) {
            this.logger.error("couldn't read document", e);
        }
        proxyFilter.endWikiSpace(KEY_MAIN_SPACE, FilterEventParameters.EMPTY);

        try {
            FileUtils.deleteDirectory(dokuwikiDirectory);
        } catch (IOException e) {
            this.logger.error("Could not delete dokuwiki folder after completion", e);
        }
    }

    private void readUsers(File userInformation, DokuWikiFilter proxyFilter) throws FilterException
    {
        List<String> lines = null;
        try {
            lines = FileUtils.readLines(userInformation, StandardCharsets.UTF_8);
        } catch (IOException e) {
            this.logger.error("Couldn't read user information", e);
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

    private void readDocument(File directory, String dokuwikiDataDirectory, DokuWikiFilter proxyFilter)
            throws FilterException, IOException
    {
        File[] directoryFiles = directory.listFiles();
        //Maintain order across file systems
        if (directoryFiles != null) {
            Arrays.sort(directoryFiles);
            for (File file : directoryFiles) {
                if (file.isDirectory()) {
                    proxyFilter.beginWikiSpace(file.getName(), FilterEventParameters.EMPTY);
                    readDocument(file, dokuwikiDataDirectory, proxyFilter);
                    proxyFilter.endWikiSpace(file.getName(), FilterEventParameters.EMPTY);
                } else if (file.getName().endsWith(KEY_TEXT_FILE_FORMAT) && !file.getName().startsWith(KEY_FULL_STOP)) {
                    String[] pathArray = file.getName()
                            .split(Matcher.quoteReplacement(System.getProperty(KEY_FILE_SEPERATOR)));
                    String documentName = pathArray[pathArray.length - 1].replace(KEY_TEXT_FILE_FORMAT, "");
                    String fileMetadataPath = file.getAbsolutePath()
                            .replace(dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR)
                                            + KEY_PAGES_DIRECTORY,
                                    dokuwikiDataDirectory + System.getProperty(KEY_FILE_SEPERATOR) + "meta")
                            .replace(KEY_TEXT_FILE_FORMAT, ".meta");
//                    FilterEventParameters documentParameters = new FilterEventParameters();
//                    documentParameters.put(WikiDocumentFilter.PARAMETER_LOCALE, Locale.ROOT);

                    //wiki document
                    proxyFilter.beginWikiDocument(documentName, FilterEventParameters.EMPTY);

                    FilterEventParameters documentLocaleParameters = new FilterEventParameters();

                    File f = new File(fileMetadataPath);
                    if(f.exists() && !f.isDirectory()) {
                        String metadataFileContents = FileUtils.readFileToString
                                (new File(fileMetadataPath), "UTF-8");
                        MixedArray documentMetadata = Pherialize.unserialize(metadataFileContents).toArray();
                        readDocumentParametersFromMetadata(documentMetadata, documentLocaleParameters);

                        //Wiki document revision

                        if ((documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).containsKey(KEY_CREATED)
                                && documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).containsKey(KEY_MODIFIED))
                                && documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getLong(KEY_CREATED)
                                < documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getLong(KEY_MODIFIED))
                        {
                            //Wiki Document Locale
                            proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
                            //read revisions
                            readPageRevision(file, dokuwikiDataDirectory, proxyFilter);
                        } else {
                            documentLocaleParameters = readDocument(file,documentLocaleParameters,
                                    dokuwikiDataDirectory, proxyFilter);
                        }
                    }
                    else {
                        this.logger.warn("File [{}] not found (Some datafile's properties (eg. filesize, " +
                                "last modified date) are not imported. Details can be found on " +
                                "https://www.dokuwiki.org/devel:metadata)", f);
                        documentLocaleParameters = readDocument(file,documentLocaleParameters,
                                dokuwikiDataDirectory, proxyFilter);
                    }

                    proxyFilter.endWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
                    proxyFilter.endWikiDocument(documentName, FilterEventParameters.EMPTY);
                }
            }
        }
    }

    private FilterEventParameters readDocument( File file ,FilterEventParameters documentLocaleParameters,
                                        String dokuwikiDataDirectory, DokuWikiFilter proxyFilter) throws FilterException
    {
        try {
            String pageContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            String convertedContent = parseContent(pageContents);
            documentLocaleParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertedContent);
            //Wiki Document Locale
            proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
            readAttachments(pageContents, file, dokuwikiDataDirectory, proxyFilter);
            return documentLocaleParameters;
        }  catch (IOException e) {
            this.logger.error("Could not read file", e);
        }
        return documentLocaleParameters;
    }

    private void readPageRevision(File file, String dokuwikiDataDirectory, DokuWikiFilter proxyFilter)
    {
        //check revision exists, check the attic, parse attic files.
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
            //Maintain the order of files
            Arrays.sort(revisionFiles);
            for (File revisionFile : revisionFiles) {
                String revision = revisionFile.getName().replace(fileName + KEY_FULL_STOP, "")
                        .replace(".txt.gz", "");
                try {

                    String documentContent = extractGZip(revisionFile);
                    String convertedContent = parseContent(documentContent);
                    FilterEventParameters revisionParameters = new FilterEventParameters();
                    revisionParameters.put(WikiDocumentFilter.PARAMETER_CONTENT, convertedContent);
                    proxyFilter.beginWikiDocumentRevision(revision, revisionParameters);
                    readAttachments(documentContent, revisionFile, dokuwikiDataDirectory, proxyFilter);
                    proxyFilter.endWikiDocumentRevision(revision, revisionParameters);
                } catch (FilterException | IOException e) {
                    this.logger.error("could not parse document revision", e);
                }
            }
        }
    }

    private String parseContent(String pageContents)
    {
        String content = "";
        try {
            //parse pageContent
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
                    if (!attachmentName.startsWith(KEY_FULL_STOP)
                            && !attachmentName.startsWith("_")
                            && content.contains(attachmentName)
                            && !attachment.isDirectory())
                    {
                        try {
                            FileInputStream attachmentStream = FileUtils.openInputStream(attachment);
                            proxyFilter.onWikiAttachment(attachment.getName(),
                                    attachmentStream,
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

    private void readDocumentParametersFromMetadata(
            MixedArray documentMetadata, FilterEventParameters documentParameters)
    {
        if (documentMetadata.getArray(KEY_PERSISTENT).containsKey(KEY_CREATOR)
                && !documentMetadata.getArray(KEY_PERSISTENT).get(KEY_CREATOR).equals(""))
        {
            documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_AUTHOR,
                    documentMetadata.getArray(KEY_PERSISTENT).getString(KEY_CREATOR));
        }
        documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_DATE,
                new Date(1000 * documentMetadata.getArray(KEY_PERSISTENT).getArray(KEY_DATE).getLong(KEY_CREATED)));
        if (documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).containsKey(KEY_MODIFIED)
                && !documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getString(KEY_MODIFIED).equals(""))
        {
            documentParameters.put(WikiDocumentFilter.PARAMETER_LASTREVISION,
                    documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getString(KEY_MODIFIED));
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_DATE,
                    new Date(1000 * documentMetadata.getArray(KEY_CURRENT).getArray(KEY_DATE).getLong(KEY_MODIFIED)));
        }
        if (documentMetadata.getArray(KEY_CURRENT).containsKey(KEY_LAST_CHANGE)
                && !documentMetadata.getArray(KEY_CURRENT).getString(KEY_USER).equals(""))
        {
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_AUTHOR,
                    documentMetadata.getArray(KEY_CURRENT).getArray(KEY_LAST_CHANGE).getString(KEY_USER));
        }
    }

    private void saveEntryToDisk(
            ArchiveInputStream archiveInputStream, ArchiveEntry archiveEntry, File folderToSave)
    {
        String entryName = archiveEntry.getName();
        if (entryName.startsWith(KEY_DOKUWIKI)) {
            entryName = entryName.replaceFirst(KEY_DOKUWIKI + System.getProperty(KEY_FILE_SEPERATOR), "");
        }
        if (!archiveEntry.isDirectory()) {

            try {
                FileUtils.copyToFile(archiveInputStream, new File(folderToSave, entryName));
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



