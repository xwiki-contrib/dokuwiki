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

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.dokuwiki.text.input.DokuWikiInputProperties;
import org.xwiki.contrib.dokuwiki.text.internal.DokuWikiFilter;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.filter.FilterException;
import org.xwiki.filter.input.AbstractBeanInputFilterStream;
import org.xwiki.filter.input.FileInputSource;
import org.xwiki.filter.input.InputSource;
import org.xwiki.filter.input.InputStreamInputSource;

import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;


/**
 * @version $Id: 41df1dab66b03111214dbec56fee8dbd44747638 $
 */
@Component
@Named(DokuWikiInputProperties.FILTER_STREAM_TYPE_STRING)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class DokuWikiInputFilterStream extends AbstractBeanInputFilterStream<DokuWikiInputProperties, DokuWikiFilter> {
    @Override
    protected void read(Object filter, DokuWikiFilter proxyFilter) throws FilterException {
        InputSource inputSource = this.properties.getSource();
        if (inputSource instanceof FileInputSource) {
            readFolder(inputSource, filter, proxyFilter);
        } else if (inputSource instanceof InputStreamInputSource) {
            //TODO handle input stream
        } else {
            throw new FilterException("Unsupported input source [" + inputSource.getClass() + "]");
        }
    }

    private void readFolder(InputSource source, Object filter, DokuWikiFilter proxyFilter) throws FilterException {
        File sourceFolder = ((FileInputSource) source).getFile();
        if (sourceFolder.isDirectory()) {

            File[] listOfFiles = sourceFolder.listFiles();
            if (listOfFiles != null)
                for (File file : listOfFiles) {
                    String fileName = file.getName();
                    if (file.isDirectory() && (file.getName().equals("pages"))) {
                        proxyFilter.beginWikiSpace("Main", FilterEventParameters.EMPTY);
                        readPagesFolder(file, filter, proxyFilter);
                        proxyFilter.endWikiSpace("Main", FilterEventParameters.EMPTY);
                    }
                }
        } else
            throw new FilterException("There's no file inside this folder");
    }

    private void readPagesFolder(File pages, Object filter, DokuWikiFilter proxyFilter) throws FilterException {
        File[] files = pages.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String folder = file.getName();
                    proxyFilter.beginWikiSpace(folder, FilterEventParameters.EMPTY);
                    readPagesFolder(file, filter, proxyFilter);
                    proxyFilter.endWikiSpace(folder, FilterEventParameters.EMPTY);
                } else if (file.isFile() && file.getName().endsWith(".txt")) {
                    String fileName = file.getName().replace(".txt", "");
                    proxyFilter.beginWikiDocument(fileName, FilterEventParameters.EMPTY);
                    proxyFilter.endWikiDocument(fileName, FilterEventParameters.EMPTY);
                }
            }
        }
    }
    
    @Override
    public void close() throws IOException {
        this.properties.getSource().close();
    }

    //helper functions
    private static URL concatenate(URL baseUrl, String extraPath) throws URISyntaxException,
            MalformedURLException {
        URI uri = baseUrl.toURI();
        String newPath = uri.getPath() + '/' + extraPath;
        URI newUri = uri.resolve(newPath);
        return newUri.toURL();
    }
}


