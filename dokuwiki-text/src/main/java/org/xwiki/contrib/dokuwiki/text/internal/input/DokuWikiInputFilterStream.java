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
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties;
import org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties;
import org.xwiki.contrib.dokuwiki.text.internal.DokuWikiFilter;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.event.user.UserFilter;
import org.xwiki.filter.input.AbstractBeanInputFilterStream;
import org.xwiki.filter.input.FileInputSource;
import org.xwiki.filter.input.InputSource;
import org.xwiki.filter.input.InputStreamInputSource;
import org.xwiki.filter.input.StringInputSource;
import org.xwiki.rendering.parser.StreamParser;
import org.xwiki.rendering.renderer.PrintRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import sun.nio.ch.IOUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
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
            throws IOException, FilterException {
        ArchiveEntry archiveEntry = archiveInputStream.getNextEntry();
        proxyFilter.beginWikiSpace(TAG_MAIN_SPACE, FilterEventParameters.EMPTY);
        while (archiveEntry != null) {
            String entryName = archiveEntry.getName();
            String[] pathArray = entryName.split(Matcher.quoteReplacement(System.getProperty("file.separator")));
            if (Arrays.asList(pathArray).contains(TAG_PAGES)
                    && !Arrays.asList(pathArray).get(pathArray.length - 1).startsWith(".")) {
                //Index of the 'pages' folder which contains the wiki documents and spaces.
                int indexOfPages = ArrayUtils.indexOf(pathArray, TAG_PAGES);
                int j = indexOfPages;
                for (int i = indexOfPages + 1; i < pathArray.length; j++, i++) {
                    if (i == pathArray.length - 1 && pathArray[i].contains(TAG_TEXT_FILE_FORMAT)) {
                        String documentName = pathArray[i].replace(TAG_TEXT_FILE_FORMAT, "");
                        proxyFilter.beginWikiDocument(documentName, FilterEventParameters.EMPTY);
                        String pageContents = org.apache.commons.io.IOUtils
                                .toString(archiveInputStream, TAG_STRING_ENCODING_FORMAT);
                        //parse pageContent
                        DefaultWikiPrinter printer = new DefaultWikiPrinter();
                        PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
                        // /Generate events
                        try {
                            dokuWikiStreamParser.parse(new StringReader(pageContents), renderer);
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new FilterException("Failed to convert content page", e);
                        }
                        proxyFilter.endWikiDocument(documentName, FilterEventParameters.EMPTY);
                        break;
                    }
                    proxyFilter.beginWikiSpace(pathArray[i], FilterEventParameters.EMPTY);
                }
                while (j > indexOfPages) {
                    proxyFilter.endWikiSpace(pathArray[j], FilterEventParameters.EMPTY);
                    j--;
                }
            } else if (entryName.endsWith("/conf/users.auth.php")) {
                //TODO find the reason why only streamed input are being parsed.
                List<String> lines = org.apache.commons.io.IOUtils.readLines(archiveInputStream, TAG_STRING_ENCODING_FORMAT);

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
            archiveEntry = archiveInputStream.getNextEntry();
        }
        proxyFilter.endWikiSpace(TAG_MAIN_SPACE, FilterEventParameters.EMPTY);
    }

    DokuWikiSyntaxInputProperties createDokuwikiInputProperties(String content) {
        DokuWikiSyntaxInputProperties parserProperties = new DokuWikiSyntaxInputProperties();

        // Set Source
        parserProperties.setSource(new StringInputSource(content));

        return parserProperties;
    }

    @Override
    public void close() throws IOException {
        this.properties.getSource().close();
    }
}



