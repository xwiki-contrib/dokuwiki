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
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.lang.ArrayUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.dokuwiki.syntax.DokuWikiSyntaxInputProperties;
import org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties;
import org.xwiki.contrib.dokuwiki.text.internal.DokuWikiFilter;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.input.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;

/**
 * @version $Id: 41df1dab66b03111214dbec56fee8dbd44747638 $
 */
@Component
@Named(DokuWikiInputProperties.FILTER_STREAM_TYPE_STRING)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiInputFilterStream extends AbstractBeanInputFilterStream<DokuWikiInputProperties, DokuWikiFilter> {
    private static final String TAG_ROOT_NODE = "root";
    private static final String TAG_PAGES = "pages";
    private static final String TAG_MAIN_SPACE = "Main";
    private static final String TAG_TEXT_FILE_FORMAT = ".txt";
    private static final String TAG_STRING_ENCODING_FORMAT = "UTF-8";

    //    @Inject
//    @Named("xwiki/2.1")
//    private PrintRendererFactory xwiki21Factory;
    @Inject
    @Named(DokuWikiSyntaxInputProperties.FILTER_STREAM_TYPE_STRING)
    private InputFilterStreamFactory parserFactory;

    @Override
    protected void read(Object filter, DokuWikiFilter proxyFilter) throws FilterException {
        InputSource inputSource = this.properties.getSource();
        if (inputSource instanceof FileInputSource) {
            try {
                CompressorInputStream input = new CompressorStreamFactory()
                        .createCompressorInputStream(new BufferedInputStream(
                                ((InputStreamInputSource) inputSource).getInputStream()));
                assert input != null;
                ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                        .createArchiveInputStream(new BufferedInputStream(input));
                readDataStream(archiveInputStream, filter, proxyFilter);
            } catch (ArchiveException | IOException | CompressorException e) {
                e.printStackTrace();
            }
        } else if (inputSource instanceof InputStreamInputSource) {
            try {
                CompressorInputStream input = new CompressorStreamFactory()
                        .createCompressorInputStream(((InputStreamInputSource) inputSource).getInputStream());
                ArchiveInputStream archiveInputStream = new ArchiveStreamFactory()
                        .createArchiveInputStream(new BufferedInputStream(input));
                readDataStream(archiveInputStream, filter, proxyFilter);
            } catch (Exception e) {
                throw new FilterException("Unsupported input stream [" + inputSource.getClass() + "]");
            }
        } else {
            throw new FilterException("Unsupported input source [" + inputSource.getClass() + "]");
        }
    }

    private void readDataStream(ArchiveInputStream archiveInputStream,
                                Object filter, DokuWikiFilter proxyFilter) throws IOException, FilterException {
        ArchiveEntry archiveEntry = archiveInputStream.getNextEntry();
        proxyFilter.beginWikiSpace(TAG_MAIN_SPACE, FilterEventParameters.EMPTY);
        while (archiveEntry != null) {
            String entryName = archiveEntry.getName();
            String[] pathArray = entryName.split(Matcher.quoteReplacement(System.getProperty("file.separator")));
            if (Arrays.asList(pathArray).contains(TAG_PAGES)) {
                int indexOfPages = ArrayUtils.indexOf(pathArray, TAG_PAGES);
                int j = indexOfPages;
                for (int i = indexOfPages+ 1; i < pathArray.length; j++, i++) {
                    if (i == pathArray.length - 1 && pathArray[i].contains(TAG_TEXT_FILE_FORMAT)) {
                        String documentName = pathArray[i].replace(TAG_TEXT_FILE_FORMAT, "");
                        proxyFilter.beginWikiDocument(documentName, FilterEventParameters.EMPTY);
                        String pageContents = org.apache.commons.io.IOUtils.toString(archiveInputStream, TAG_STRING_ENCODING_FORMAT);
                        //TODO Parse pageContent
                        proxyFilter.endWikiDocument(documentName, FilterEventParameters.EMPTY);
                        break;
                    }
                    proxyFilter.beginWikiSpace(pathArray[i], FilterEventParameters.EMPTY);
                }
                while (j > indexOfPages) {
                    proxyFilter.endWikiSpace(pathArray[j], FilterEventParameters.EMPTY);
                    j--;
                }
            }
            archiveEntry = archiveInputStream.getNextEntry();
        }
        proxyFilter.endWikiSpace(TAG_MAIN_SPACE, FilterEventParameters.EMPTY);
    }

//    private void readPageFolderStream(Folder pagesFolderTree, ArchiveInputStream archiveInputStream,
//                                      Object filter, DokuWikiFilter proxyFilter) throws FilterException {
//        for (Folder i : pagesFolderTree.getChilds()) {
//            proxyFilter.beginWikiDocument(i.toString(),FilterEventParameters.EMPTY );
//            readPageFolderStream(i, archiveInputStream, filter, proxyFilter);
//            proxyFilter.endWikiSpace(i.toString(), FilterEventParameters.EMPTY);
//        }
//        for (Folder j : pagesFolderTree.getLeafs()) {
//            if (j.toString().endsWith(TAG_TEXT_FILE_FORMAT)) {
//                String leafName = j.toString().replace(TAG_TEXT_FILE_FORMAT, "");
//                proxyFilter.beginWikiDocument(leafName, FilterEventParameters.EMPTY);
//
//                DefaultWikiPrinter printer = new DefaultWikiPrinter();
//                PrintRenderer renderer = this.xwiki21Factory.createRenderer(printer);
//                DokuWikiSyntaxInputProperties parserProperties = createDokuwikiInputProperties(j.getContents());
//
//                 Generate events
//                try (BeanInputFilterStream<DokuWikiInputProperties> stream =
//                             ((BeanInputFilterStreamFactory) this.parserFactory).createInputFilterStream(parserProperties)) {
//                    stream.read();
//                } catch (Exception e) {
//                    throw new FilterException("Failed to convert content page", e);
//                }
//
//                proxyFilter.endWikiDocument(leafName, FilterEventParameters.EMPTY);
//            }
//        }
//    }

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



