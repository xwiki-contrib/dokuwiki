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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.regex.Matcher;

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
        readUsers(new File (
                dokuwikiDirectory.getAbsolutePath()
                        + System.getProperty("file.separator")
                        + "conf" + System.getProperty("file.separator")
                        + "users.auth.php"),
                proxyFilter);

        //recursively parse documents
        proxyFilter.beginWikiSpace("Main", FilterEventParameters.EMPTY);
        readDocument(new File(
                dokuwikiDataDirectory.getAbsolutePath() + System.getProperty("file.separator") + "pages"), proxyFilter);
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

    private void readDocument(File directory, DokuWikiFilter proxyFilter) throws FilterException {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    proxyFilter.beginWikiSpace(file.getName(), FilterEventParameters.EMPTY);
                    readDocument(file, proxyFilter);
                    proxyFilter.endWikiSpace(file.getName(), FilterEventParameters.EMPTY);
                } else if (file.getName().endsWith(".txt") && !file.getName().startsWith(".")) {
                    String[] pathArray = file.getName()
                            .split(Matcher.quoteReplacement(System.getProperty("file.separator")));
                    String documentName = pathArray[pathArray.length - 1].replace(".txt", "");
                    FilterEventParameters documentLocaleParameters = new FilterEventParameters();

                    proxyFilter.beginWikiDocument(documentName, FilterEventParameters.EMPTY);
                    String pageContents = null;
                    try {
                        pageContents = FileUtils.readFileToString(file, "UTF-8");
                        //parse pageContent
                        DefaultWikiPrinter printer = new DefaultWikiPrinter();
                        PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
                        dokuWikiStreamParser.parse(new StringReader(pageContents), renderer);
                    } catch (IOException | ParseException e) {
                        e.printStackTrace();
                    }
                    proxyFilter.endWikiDocument(documentName, FilterEventParameters.EMPTY);
                }
            }
        }
    }

    private void readDocumentParametersFromMetaData() {

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

    @Override
    public void close() throws IOException {
        this.properties.getSource().close();
    }
}



