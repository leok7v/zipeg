/*
 * TarEntry.java
 *
 * Created on 28. Februar 2006, 12:22
 */
/*
 * Copyright 2006 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schlichtherle.io.archive.tar;

import de.schlichtherle.io.ArchiveEntryMetaData;
import de.schlichtherle.io.archive.spi.ArchiveEntry;

import java.io.File;

import javax.swing.Icon;

/**
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class TarEntry
        extends org.apache.tools.tar.TarEntry
        implements ArchiveEntry {
    
    public TarEntry(String entryName) {
        super(entryName);
    }
    
    public TarEntry(String entryName, TarEntry blueprint) {
        super(entryName);
        setMode(blueprint.getMode());
        setModTime(blueprint.getModTime());
        setSize(blueprint.getSize());
        setUserId(blueprint.getUserId());
        setUserName(blueprint.getUserName());
        setGroupId(blueprint.getGroupId());
        setGroupName(blueprint.getGroupName());
    }

    public TarEntry(File file) {
        super(file);
    }

    public Icon getOpenIcon() {
        return null;
    }

    public Icon getClosedIcon() {
        return null;
    }

    public long getTime() {
        return getModTime().getTime();
    }

    public void setTime(long time) {
        setModTime(time);
    }

    private ArchiveEntryMetaData metaData;

    public ArchiveEntryMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(ArchiveEntryMetaData metaData) {
        this.metaData = metaData;
    }
}
