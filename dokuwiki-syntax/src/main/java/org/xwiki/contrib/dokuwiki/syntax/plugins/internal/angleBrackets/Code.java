package org.xwiki.contrib.dokuwiki.syntax.plugins.internal.angleBrackets;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.dokuwiki.syntax.plugins.internal.PluginParser;
import org.xwiki.rendering.listener.Listener;

@Component
@Named("code")
@Singleton
class Code implements PluginParser
{
    @Override public void parse(Listener listener, Reader source, ArrayList<Character> buffer) throws IOException
    {

    }
}
