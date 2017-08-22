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

import de.ailis.pherialize.MixedArray;
import de.ailis.pherialize.Pherialize;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;

/**
 * @version $Id: 41df1dab66b03111214dbec56fee8dbd44747638 $
 */

@Component
@Named(DokuWikiInputProperties.FILTER_STREAM_TYPE_STRING)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiInputFilterStream extends AbstractBeanInputFilterStream<DokuWikiInputProperties, DokuWikiFilter> {
    private static final String TAG_PAGES = "pages";
    private static final String TAG_MAIN_SPACE = "Main";
    private static final String TAG_TEXT_FILE_FORMAT = ".txt";
    private static final String TAG_STRING_ENCODING_FORMAT = "UTF-8";

    @Inject
    @Named("xwiki/2.1")
    private PrintRendererFactory xwiki21Factory;

    @Inject
    @Named(org.xwiki.contrib.dokuwiki.syntax.internal.parser.DokuWikiStreamParser.SYNTAX_STRING)
    private StreamParser dokuWikiStreamParser;

    @Override
    protected void read(Object filter, DokuWikiFilter proxyFilter) throws FilterException {
        InputSource inputSource = this.properties.getSource();
        if (inputSource instanceof FileInputSource) {
            try {
                CompressorInputStream input = new CompressorStreamFactory()
                        .createCompressorInputStream(new BufferedInputStream(
                                ((InputStreamInputSource) inputSource).getInputStream()));
                ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                        .createArchiveInputStream(new BufferedInputStream(input));
                readDataStream(archiveInputStream, filter, proxyFilter);
            } catch (Exception e1) {
                try {
                    ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                            .createArchiveInputStream(new BufferedInputStream(
                                    ((InputStreamInputSource) inputSource).getInputStream()));
                    readDataStream(archiveInputStream, filter, proxyFilter);

                } catch (Exception e2) {
                    try {
                        CompressorInputStream input = new CompressorStreamFactory()
                                .createCompressorInputStream(new BufferedInputStream(
                                        ((InputStreamInputSource) inputSource).getInputStream()));
                        //Implement readDataStream method for Compressor input stream
                    } catch (CompressorException | IOException e) {
                        e.printStackTrace();
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
            } catch (Exception e1) {
                try {
                    ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                            .createArchiveInputStream(
                                    new BufferedInputStream((InputStream) ((InputStreamInputSource) inputSource)
                                            .getInputStream()));
                    readDataStream(archiveInputStream, filter, proxyFilter);
                } catch (Exception e2) {
                    try {
                        CompressorInputStream input = new CompressorStreamFactory()
                                .createCompressorInputStream(((InputStreamInputSource) inputSource).getInputStream());
                        //Implement readDataStream method for Compressor input stream

                    } catch (IOException | CompressorException e3) {
                        e1.printStackTrace();
                        e2.printStackTrace();
                        e3.printStackTrace();
                    }
                }
            }
        } else {
            throw new FilterException("Unsupported input source [" + inputSource.getClass() + "]");
        }
    }

    private void readDataStream(ArchiveInputStream archiveInputStream,
                                Object filter, DokuWikiFilter proxyFilter)
            throws FilterException {


        ArchiveEntry archiveEntry = null;
        //dokuwiki directory
        File dokuwikiDirectory = new File(
                FileUtils.getUserDirectoryPath() + System.getProperty("file.separator") + "dokuwiki");
        //Dokuwiki's data directory
        File dokuwikiDataDirectory = new File(
                dokuwikiDirectory.getAbsolutePath() + System.getProperty("file.separator") + "data");
        //delete folder is exists.
        try {
            FileUtils.deleteDirectory(dokuwikiDirectory);
            archiveEntry = archiveInputStream.getNextEntry();
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (archiveEntry != null) {
            /*
            All filters parsing any file will make the respecting entry file blank,
            the file is saved in dokuwiki temporary folder now.
            */
            saveEntryToDisk(archiveInputStream, archiveEntry, "dokuwiki");

            //get next file from archive stream
            try {
                archiveEntry = archiveInputStream.getNextEntry();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //readUsers
        readUsers(new File(
                        dokuwikiDirectory.getAbsolutePath()
                                + System.getProperty("file.separator")
                                + "conf" + System.getProperty("file.separator")
                                + "users.auth.php"),
                proxyFilter);

        //recursively parse documents
        proxyFilter.beginWikiSpace("Main", FilterEventParameters.EMPTY);
        try {
            readDocument(new File(
                            dokuwikiDataDirectory.getAbsolutePath() + System.getProperty("file.separator") + "pages"),
                    dokuwikiDataDirectory.getAbsolutePath(), proxyFilter);
        } catch (IOException e) {
            e.printStackTrace();
        }
        proxyFilter.endWikiSpace("Main", FilterEventParameters.EMPTY);
        //delete the folder
        try {
            FileUtils.deleteDirectory(dokuwikiDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readUsers(File userInformation, DokuWikiFilter proxyFilter) throws FilterException {
        List<String> lines = null;
        try {
            lines = FileUtils.readLines(userInformation, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (lines != null) {
            for (String line : lines) {
                if (!(line.length() == 0 || line.startsWith("#"))) {
                    String[] parameters = line.split(":");
                    FilterEventParameters userParameters = new FilterEventParameters();
                    userParameters.put(UserFilter.PARAMETER_FIRSTNAME, parameters[2].split(" ")[0]);
                    userParameters.put(UserFilter.PARAMETER_LASTNAME, parameters[2].split(" ")[1]);
                    userParameters.put(UserFilter.PARAMETER_EMAIL, parameters[3]);
                    proxyFilter.beginUser(parameters[0], userParameters);
                    proxyFilter.endUser(parameters[0], userParameters);
                }
            }
        }
    }

    private void readDocument(File directory, String dokuwikiDataDirectory, DokuWikiFilter proxyFilter) throws FilterException, IOException {
        File[] directoryFiles = directory.listFiles();
        if (directory != null) {
            for (File file : directoryFiles) {
                if (file.isDirectory()) {
                    proxyFilter.beginWikiSpace(file.getName(), FilterEventParameters.EMPTY);
                    readDocument(file, dokuwikiDataDirectory, proxyFilter);
                    proxyFilter.endWikiSpace(file.getName(), FilterEventParameters.EMPTY);
                } else if (file.getName().endsWith(".txt") && !file.getName().startsWith(".")) {
                    String[] pathArray = file.getName()
                            .split(Matcher.quoteReplacement(System.getProperty("file.separator")));
                    String documentName = pathArray[pathArray.length - 1].replace(".txt", "");
                    String fileMetadataPath = file.getAbsolutePath()
                            .replace(dokuwikiDataDirectory + System.getProperty("file.separator") + "pages",
                                    dokuwikiDataDirectory + System.getProperty("file.separator") + "meta")
                            .replace(".txt", ".meta");
//                    FilterEventParameters documentParameters = new FilterEventParameters();
//                    documentParameters.put(WikiDocumentFilter.PARAMETER_LOCALE, Locale.ROOT);

                    //wiki document
                    proxyFilter.beginWikiDocument(documentName, FilterEventParameters.EMPTY);

                    FilterEventParameters documentLocaleParameters = new FilterEventParameters();
                    String metadataFileContents = FileUtils.readFileToString(new File(fileMetadataPath), "UTF-8");
                    MixedArray documentMetadata = Pherialize.unserialize(metadataFileContents).toArray();
                    readDocumentParametersFromMetadata(documentMetadata, documentLocaleParameters);

                    //Wiki Document Locale
                    proxyFilter.beginWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);

                    //Wiki document revision

                    if ((documentMetadata.getArray("current").getArray("date").containsKey("created")
                            && documentMetadata.getArray("current").getArray("date").containsKey("modified"))
                            && documentMetadata.getArray("current").getArray("date").getLong("created")
                            < documentMetadata.getArray("current").getArray("date").getLong("modified")) {
                        //read attachments
                        //read revisions
                        readPageRevision(file, dokuwikiDataDirectory, proxyFilter);
                    } else {
                        String pageContents = FileUtils.readFileToString(file, "UTF-8");
                        readAttachments(pageContents, file, dokuwikiDataDirectory, proxyFilter);
                        parseContent(pageContents);
                    }

                    proxyFilter.endWikiDocumentLocale(Locale.ROOT, documentLocaleParameters);
                    proxyFilter.endWikiDocument(documentName, FilterEventParameters.EMPTY);
                }
            }
        }
    }

    private void readPageRevision(File file, String dokuwikiDataDirectory, DokuWikiFilter proxyFilter) {
        //check revision exists, check the attic, parse attic files.
        String fileName = file.getName().replace(".txt", "");
        String fileRevisionDirectory = file.getAbsolutePath()
                .replace(dokuwikiDataDirectory + System.getProperty("file.separator") + "pages",
                        dokuwikiDataDirectory + System.getProperty("file.separator") + "attic")
                .replace(System.getProperty("file.separator") + file.getName(), "");

        File atticDirectoryForFile = new File(fileRevisionDirectory);
        File[] revisionFiles = atticDirectoryForFile.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(fileName);
            }
        });
        if (revisionFiles != null) {
            for (File revisionFile : revisionFiles) {
                String revision = revisionFile.getName().replace(fileName + ".", "")
                        .replace(".txt.gz", "");
                try {
                    proxyFilter.beginWikiDocumentRevision(revision, FilterEventParameters.EMPTY);
                    String documentContent = extractGZip(revisionFile);
                    readAttachments(documentContent, revisionFile, dokuwikiDataDirectory, proxyFilter);
                    parseContent(documentContent);
                    proxyFilter.endWikiDocumentRevision(revision, FilterEventParameters.EMPTY);
                } catch (FilterException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void parseContent(String pageContents) {
        try {
            //parse pageContent
            DefaultWikiPrinter printer = new DefaultWikiPrinter();
            PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
            dokuWikiStreamParser.parse(new StringReader(pageContents), renderer);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void readAttachments(String content, File document, String dokuwikiDataDirectory, DokuWikiFilter proxyFilter) {
        String mediaNameSpaceDirectoryPath = document.getAbsolutePath()
                .replace(dokuwikiDataDirectory + System.getProperty("file.separator") + "pages",
                        dokuwikiDataDirectory + System.getProperty("file.separator") + "media")
                .replace(dokuwikiDataDirectory + System.getProperty("file.separator") + "attic",
                        dokuwikiDataDirectory + System.getProperty("file.separator") + "media")
                .replace(document.getName(), "");

        File mediaSpace = new File(mediaNameSpaceDirectoryPath);
        if (mediaSpace.exists() && mediaSpace.isDirectory()) {
            File[] mediaFiles = mediaSpace.listFiles();
            if (mediaFiles != null) {
                for (File attachment : mediaFiles) {
                    String attachmentName = attachment.getName();
                    if (!attachmentName.startsWith(".")
                            && !attachmentName.startsWith("_")
                            && content.contains(attachmentName)
                            && !attachment.isDirectory()) {
                        try {
                            proxyFilter.onWikiAttachment(attachment.getName(),
                                    FileUtils.openInputStream(attachment),
                                    FileUtils.sizeOf(attachment), FilterEventParameters.EMPTY);
                        } catch (FilterException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void readDocumentParametersFromMetadata(
            MixedArray documentMetadata, FilterEventParameters documentParameters) {
        if (documentMetadata.getArray("persistent").containsKey("creator") &&
                !documentMetadata.getArray("persistent").get("creator").equals("")) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_AUTHOR,
                    documentMetadata.getArray("persistent").getString("creator"));
        }
        documentParameters.put(WikiDocumentFilter.PARAMETER_CREATION_DATE,
                new Date(1000 * documentMetadata.getArray("persistent").getArray("date").getLong("created")));
        if (documentMetadata.getArray("current").getArray("date").containsKey("modified") &&
                !documentMetadata.getArray("current").getArray("date").getString("modified").equals("")) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_LASTREVISION,
                    documentMetadata.getArray("current").getArray("date").getString("modified"));
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_DATE,
                    new Date(1000 * documentMetadata.getArray("current").getArray("date").getLong("modified")));
        }
        if (documentMetadata.getArray("current").containsKey("last_change") &&
                !documentMetadata.getArray("current").getString("user").equals("")) {
            documentParameters.put(WikiDocumentFilter.PARAMETER_REVISION_AUTHOR,
                    documentMetadata.getArray("current").getArray("last_change").getString("user"));
        }
    }

    private void saveEntryToDisk(ArchiveInputStream archiveInputStream, ArchiveEntry archiveEntry, String folder) {
        String entryName = archiveEntry.getName();
        if (!entryName.startsWith(folder)) {
            entryName = folder + System.getProperty("file.separator") + entryName;
        }
        entryName = FileUtils.getUserDirectoryPath() + System.getProperty("file.separator") + entryName;
        if (!archiveEntry.isDirectory()) {
            BufferedOutputStream fos = null;
            try {
                File entryFile = new File(entryName);
                entryFile.getParentFile().mkdirs();
                fos = new BufferedOutputStream(new FileOutputStream(entryFile));
                byte[] buffer = new byte[1024];
                int length;
                while ((length = archiveInputStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String extractGZip(File file) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(file));
        return IOUtils.toString(gzipInputStream, "UTF-8");
    }

    @Override
    public void close() throws IOException {
        this.properties.getSource().close();
    }
}



