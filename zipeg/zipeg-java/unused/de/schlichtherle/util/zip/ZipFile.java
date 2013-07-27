/*
 * Copyright 2005-2006 Schlichtherle IT Services
 * Copyright 2003-2005 The Apache Software Foundation
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

package de.schlichtherle.util.zip;

import de.schlichtherle.io.rof.BufferedReadOnlyFile;
import de.schlichtherle.io.rof.ReadOnlyFile;
import de.schlichtherle.io.rof.SimpleReadOnlyFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/**
 * Drop-in replacement for {@link java.util.zip.ZipFile java.util.zip.ZipFile}.
 * <p>
 * This class adds support for file name encodings other than UTF-8
 * (which is required to work on ZIP compatible files created by native
 * zip tools and is able to skip a preamble like the one found in self
 * extracting archives.  Furthermore it returns instances of
 * <code>de.schlichtherle.util.zip.ZipEntry</code> instead of
 * <code>java.util.zip.ZipEntry</code>.
 * <p>
 * It doesn't extend <code>java.util.zip.ZipFile</code> as it would
 * have to reimplement all methods anyway.  Like
 * <code>java.util.ZipFile</code>, it uses RandomAccessFile under the
 * covers and supports compressed and uncompressed entries.
 * <p>
 * This class is designed to be thread safe.
 * <p>
 * This class is mostly backwards compatible to
 * <code>java.util.zip.ZipFile</code>, with the following exceptions:
 * <ul>
 * <li>There is no <code>getName()</code> method.</li>
 * <li>entries() and getEntry(String) return {@link ZipEntry} instances
 *     which are shared with this class, not clones.</li>
 * <li>This class is designed to be thread-safe.</li>
 * </ul>
 */
public class ZipFile implements ZipConstants {

    private static long LONG_MSB = 0x8000000000000000L;

    private static final int LFH_FILE_NAME_LENGTH_OFFSET =
        /* local file header signature     */ 4 +
        /* version needed to extract       */ 2 +
        /* general purpose bit flag        */ 2 +
        /* compression method              */ 2 +
        /* last mod file time              */ 2 +
        /* last mod file date              */ 2 +
        /* crc-32                          */ 4 +
        /* compressed size                 */ 4 +
        /* uncompressed size               */ 4;

    private static final int EOCD_NUM_ENTRIES_OFFSET =
        /* end of central dir signature    */ 4 +
        /* number of this disk             */ 2 +
        /* number of the disk with the     */   +
        /* start of the central directory  */ 2 +
        /* total number of entries in      */   +
        /* the central dir on this disk    */ 2;

    private static final int EOCD_CD_SIZE_OFFSET =
            EOCD_MIN_LEN - 10;

    private static final int EOCD_CD_LOCATION_OFFSET =
        /* end of central dir signature    */ 4 +
        /* number of this disk             */ 2 +
        /* number of the disk with the     */   +
        /* start of the central directory  */ 2 +
        /* total number of entries in      */   +
        /* the central dir on this disk    */ 2 +
        /* total number of entries in      */   +
        /* the central dir                 */ 2 +
        /* size of the central directory   */ 4;
    
    private static final int EOCD_COMMENT_OFFSET =
            EOCD_MIN_LEN - 2;

    /**
     * The encoding to use for entry names and comments.
     */
    private final String encoding;

    /** 
     * The comment of this ZIP compatible file.
     */
    private String comment;

    /**
     * Maps entry names to zip entries [String -> ZipEntry].
     */
    private final Map entries = new HashMap();

    /** The actual data source. */
    private volatile ReadOnlyFile archive;

    /**
     * The number of open streams reading from this ZIP compatible file. 
     */
    private volatile int openStreams;

    /**
     * The number of bytes in the preamble of this ZIP compatible file.
     */
    private long preamble;

    /**
     * The number of bytes in the postamble of this ZIP compatible file.
     */
    private long postamble;

    private OffsetMapper mapper;

    /**
     * Opens the given file for reading its ZIP contents,
     * assuming UTF-8 encoding for file names.
     * 
     * @param name name of the file.
     * 
     * @throws NullPointerException If <code>name</code> is <code>null</code>.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(String name)
    throws  NullPointerException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = DEFAULT_ENCODING;
        try {
            init(null, new File(name), true, false);
        } catch (UnsupportedEncodingException cannotHappen) {
            throw new AssertionError(cannotHappen);
        }
    }

    /**
     * Opens the given file for reading its ZIP contents,
     * assuming the specified encoding for file names.
     * 
     * @param name name of the file.
     * @param encoding the encoding to use for file names
     * 
     * @throws NullPointerException If <code>name</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(String name, String encoding)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = encoding;
        init(null, new File(name), true, false);
    }

    /**
     * Opens the given file for reading its ZIP contents,
     * assuming the specified encoding for file names.
     * 
     * @param name name of the file.
     * @param encoding the encoding to use for file names
     * @param preambled If this is <code>true</code>, then the ZIP compatible
     *        file may have a preamble.
     *        Otherwise, the ZIP compatible file must start with either a
     *        Local File Header (LFH) signature or an End Of Central Directory
     *        (EOCD) Header, causing this constructor to fail fast if the file
     *        is actually a false positive ZIP compatible file, i.e. not
     *        compatible to the ZIP File Format Specification.
     *        This may be used to read Self Extracting ZIP files (SFX), which
     *        usually contain the application code required for extraction in
     *        a preamble.
     *        This parameter is <code>true</code> by default.
     * @param postambled If this is <code>true</code>, then the ZIP compatible
     *        file may have a postamble of arbitrary length.
     *        Otherwise, the ZIP compatible file must not have a postamble
     *        which exceeds 64KB size, including the End Of Central Directory
     *        record (i.e. including the ZIP file comment), causing this
     *        constructor to fail fast if the file is actually a false positive
     *        ZIP compatible file, i.e. not compatible to the ZIP File Format
     *        Specification.
     *        This may be used to read Self Extracting ZIP files (SFX) with
     *        large postambles.
     *        This parameter is <code>false</code> by default.
     *
     * @throws NullPointerException If <code>name</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(
            String name,
            String encoding,
            boolean preambled,
            boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = encoding;
        init(null, new File(name), preambled, postambled);
    }

    /**
     * Opens the given file for reading its ZIP contents,
     * assuming UTF-8 encoding for file names.
     * 
     * @param file The file.
     * 
     * @throws NullPointerException If <code>file</code> is <code>null</code>.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(File file)
    throws  NullPointerException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = DEFAULT_ENCODING;
        try {
            init(null, file, true, false);
        } catch (UnsupportedEncodingException cannotHappen) {
            throw new AssertionError(cannotHappen);
        }
    }

    /**
     * Opens the given file for reading its ZIP contents,
     * assuming the specified encoding for file names.
     * 
     * @param file The file.
     * @param encoding The encoding to use for entry names and comments
     *        - must <em>not</em> be <code>null</code>!
     * 
     * @throws NullPointerException If <code>file</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(File file, String encoding)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = encoding;
        init(null, file, true, false);
    }

    /**
     * Opens the given file for reading its ZIP contents,
     * assuming the specified encoding for file names.
     * 
     * @param file The file.
     * @param encoding The encoding to use for entry names and comments
     *        - must <em>not</em> be <code>null</code>!
     * @param preambled If this is <code>true</code>, then the ZIP compatible
     *        file may have a preamble.
     *        Otherwise, the ZIP compatible file must start with either a
     *        Local File Header (LFH) signature or an End Of Central Directory
     *        (EOCD) Header, causing this constructor to fail fast if the file
     *        is actually a false positive ZIP compatible file, i.e. not
     *        compatible to the ZIP File Format Specification.
     *        This may be used to read Self Extracting ZIP files (SFX), which
     *        usually contain the application code required for extraction in
     *        a preamble.
     *        This parameter is <code>true</code> by default.
     * @param postambled If this is <code>true</code>, then the ZIP compatible
     *        file may have a postamble of arbitrary length.
     *        Otherwise, the ZIP compatible file must not have a postamble
     *        which exceeds 64KB size, including the End Of Central Directory
     *        record (i.e. including the ZIP file comment), causing this
     *        constructor to fail fast if the file is actually a false positive
     *        ZIP compatible file, i.e. not compatible to the ZIP File Format
     *        Specification.
     *        This may be used to read Self Extracting ZIP files (SFX) with
     *        large postambles.
     *        This parameter is <code>false</code> by default.
     *
     * @throws NullPointerException If <code>file</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(
            File file,
            String encoding,
            boolean preambled,
            boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = encoding;
        init(null, file, preambled, postambled);
    }

    /**
     * Opens the given read only file for reading its ZIP contents,
     * assuming UTF-8 encoding for file names.
     * 
     * @param rof The read only file.
     *        Note that this constructor <em>never</em> closes this file.
     * 
     * @throws NullPointerException If <code>file</code> is <code>null</code>.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(ReadOnlyFile rof)
    throws  NullPointerException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = DEFAULT_ENCODING;
        try {
            init(rof, null, true, false);
        } catch (UnsupportedEncodingException cannotHappen) {
            throw new AssertionError(cannotHappen);
        }
    }

    /**
     * Opens the given read only file for reading its ZIP contents,
     * assuming the specified encoding for file names.
     * 
     * @param rof The read only file.
     *        Note that this constructor <em>never</em> closes this file.
     * @param encoding The encoding to use for entry names and comments
     *        - must <em>not</em> be <code>null</code>!
     * 
     * @throws NullPointerException If <code>file</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(ReadOnlyFile rof, String encoding)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = encoding;
        init(rof, null, true, false);
    }

    /**
     * Opens the given read only file for reading its ZIP contents,
     * assuming the specified encoding for file names.
     * 
     * @param rof The read only file.
     *        Note that this constructor <em>never</em> closes this file.
     * @param encoding The encoding to use for entry names and comments
     *        - must <em>not</em> be <code>null</code>!
     * @param preambled If this is <code>true</code>, then the ZIP compatible
     *        file may have a preamble.
     *        Otherwise, the ZIP compatible file must start with either a
     *        Local File Header (LFH) signature or an End Of Central Directory
     *        (EOCD) Header, causing this constructor to fail fast if the file
     *        is actually a false positive ZIP compatible file, i.e. not
     *        compatible to the ZIP File Format Specification.
     *        This may be used to read Self Extracting ZIP files (SFX), which
     *        usually contain the application code required for extraction in
     *        a preamble.
     *        This parameter is <code>true</code> by default.
     * @param postambled If this is <code>true</code>, then the ZIP compatible
     *        file may have a postamble of arbitrary length.
     *        Otherwise, the ZIP compatible file must not have a postamble
     *        which exceeds 64KB size, including the End Of Central Directory
     *        record (i.e. including the ZIP file comment), causing this
     *        constructor to fail fast if the file is actually a false positive
     *        ZIP compatible file, i.e. not compatible to the ZIP File Format
     *        Specification.
     *        This may be used to read Self Extracting ZIP files (SFX) with
     *        large postambles.
     *        This parameter is <code>false</code> by default.
     *
     * @throws NullPointerException If <code>file</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(
            ReadOnlyFile rof,
            String encoding,
            boolean preambled,
            boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this.encoding = encoding;
        init(rof, null, preambled, postambled);
    }

    private void init(
            ReadOnlyFile rof,
            final File file,
            final boolean preambled,
            final boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        // Check parameters (fail fast).
        if (encoding == null)
            throw new NullPointerException("encoding");
        new String(new byte[0], encoding); // may throw UnsupportedEncodingException!
        if (rof == null) {
            if (file == null)
                throw new NullPointerException();
            rof = createReadOnlyFile(file);
        } else { // rof != null
            assert file == null;
        }
        archive = rof;

        try {
            final BufferedReadOnlyFile brof;
            if (archive instanceof BufferedReadOnlyFile)
                brof = (BufferedReadOnlyFile) archive;
            else
                brof = new BufferedReadOnlyFile(archive);
            loadCentralDirectory(brof, preambled, postambled);
            // Do NOT close brof - would close rof as well!
        } catch (IOException failure) {
            if (file != null)
                rof.close();
            throw failure;
        }
        
        assert mapper != null;
    }

    /**
     * A factory method called by the constructor to get a read only file
     * to access the contents of the ZIP file.
     * This method is only used if the constructor isn't called with a read
     * only file as its parameter.
     * 
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws IOException On any other I/O related issue.
     */
    protected ReadOnlyFile createReadOnlyFile(File file)
    throws FileNotFoundException, IOException {
        return new SimpleReadOnlyFile(file);
    }

    /**
     * Reads the central directory of the given file and populates
     * the internal tables with ZipEntry instances.
     * <p>
     * The ZipEntrys will know all data that can be obtained from
     * the central directory alone, but not the data that requires the
     * local file header or additional data to be read.
     * 
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    private void loadCentralDirectory(
            final ReadOnlyFile rof,
            final boolean preambled,
            final boolean postambled)
    throws ZipException, IOException {
        int numEntries = findCentralDirectory(rof, preambled, postambled);
        assert mapper != null;

        preamble = Long.MAX_VALUE;

        final byte[] sig = new byte[4];
        final byte[] cfh = new byte[CFH_MIN_LEN - sig.length];
        for (; ; numEntries--) {
            rof.readFully(sig);
            if (readUInt(sig) != CFH_SIG)
                break;

            rof.readFully(cfh);
            final int entryNameLen = readUShort(cfh, 24);
            final byte[] entryName = new byte[entryNameLen];
            rof.readFully(entryName);

            final ZipEntry ze = createZipEntry(new String(entryName, encoding));
            try {
                int off = 0;

                final int versionMadeBy = readUShort(cfh, off);
                off += 2;
                ze.setPlatform((short) ((versionMadeBy >> 8) & 0xFF));

                off += 4; // skip version info and general purpose byte

                ze.setMethod((short) readUShort(cfh, off));
                off += 2;

                ze.setDosTime(readUInt(cfh, off));
                off += 4;

                ze.setCrc(readUInt(cfh, off));
                off += 4;

                ze.setCompressedSize(readUInt(cfh, off));
                off += 4;

                ze.setSize(readUInt(cfh, off));
                off += 4;

                off += 2;   // file name length

                final int extraLen = readUShort(cfh, off);
                off += 2;

                final int commentLen = readUShort(cfh, off);
                off += 2;

                off += 2;   // disk number

                //ze.setInternalAttributes(readUShort(cfh, off));
                off += 2;

                //ze.setExternalAttributes(readUInt(cfh, off));
                off += 4;

                // Local file header offset.
                final long lfhOff = mapper.location(readUInt(cfh, off));

                // Set MSB in entry offset in order to indicate that
                // getInputStream(*) should resolve this.
                // Note that the result can never be -1 as a long value.
                ze.offset = lfhOff | LONG_MSB;
                
                // Update preamble size conditionally.
                if (lfhOff < preamble)
                    preamble = lfhOff;

                entries.put(ze.getName(), ze);

                if (extraLen > 0) {
                    final byte[] extra = new byte[extraLen];
                    rof.readFully(extra);
                    ze.setExtra(extra);
                }

                if (commentLen > 0) {
                    final byte[] comment = new byte[commentLen];
                    rof.readFully(comment);
                    ze.setComment(new String(comment, encoding));
                }
            } catch (IllegalArgumentException incompatibleZipFile) {
                final ZipException exc = new ZipException(ze.getName());
                exc.initCause(incompatibleZipFile);
                throw exc;
            }
        }

        // Check if the number of entries found matches the number of entries
        // declared in the End Of Central Directory header.
        // If this is a (possibly negative) multiple of 65536, then the
        // number of entries stored in the ZIP file exceeds the maximum
        // number of 65535 entries supported by the ZIP File Format
        // Specification (a two byte unsigned integer).
        // Although beyond the spec, we silently tolerate this.
        // Thanks to Jean-Francois Thamie for submitting this issue!
        if (numEntries % 65536 != 0)
            throw new ZipException(
                    "Not a ZIP compatible file: Expected " +
                    Math.abs(numEntries) +
                    (numEntries > 0 ? " more" : " less") +
                    " entries in the Central Directory!");

        if (preamble == Long.MAX_VALUE)
            preamble = 0;
    }

    /**
     * Searches for the &quot;End of central dir record&quot;, parses
     * it and positions the file pointer at the first central directory
     * record.
     * Performs some means to check that this is really a ZIP compatible
     * file.
     * <p>
     * As a side effect, both <code>mapper</code> and </code>postamble</code>
     * will be set.
     * 
     * @throws ZipException If the file is not ZIP compatible.
     * @throws IOException On any other I/O related issue.
     */
    private int findCentralDirectory(
            final ReadOnlyFile rof,
            boolean preambled,
            final boolean postambled)
    throws ZipException, IOException {
        final byte[] sig = new byte[4];
        if (!preambled) {
            rof.seek(0);
            rof.readFully(sig);
            final long signature = readUInt(sig);
            // Constraint: A ZIP file must start with a Local File Header (LFH)
            // or an End Of Central Directory (EOCD) record in case it's emtpy.
            preambled = signature == LFH_SIG || signature == EOCD_SIG;
        }
        if (preambled) {
            final long length = rof.length();
            final long max = length - EOCD_MIN_LEN;
            final long min = !postambled && max >= 0xFFFF ? max - 0xFFFF : 0;
            for (long eocdOff = max; eocdOff >= min; eocdOff--) {
                rof.seek(eocdOff);
                rof.readFully(sig);
                if (readUInt(sig) != EOCD_SIG)
                    continue;
                
                // Process EOCD.
                final byte[] eocd = new byte[EOCD_MIN_LEN - sig.length];
                rof.readFully(eocd);
                final int numEntries = readUShort(eocd, EOCD_NUM_ENTRIES_OFFSET - sig.length);
                final long cdSize = readUInt(eocd, EOCD_CD_SIZE_OFFSET - sig.length);
                final long cdLoc = readUInt(eocd, EOCD_CD_LOCATION_OFFSET - sig.length);
                final int commentLen = readUShort(eocd, EOCD_COMMENT_OFFSET - sig.length);
                if (commentLen > 0) {
                    final byte[] comment = new byte[commentLen];
                    rof.readFully(comment);
                    setComment(new String(comment, encoding));
                }
                postamble = length - rof.getFilePointer();
                
                // Seek and check first CFH, probably using an offset mapper.
                long start = eocdOff - cdSize;
                rof.seek(start);
                start -= cdLoc;
                if (start != 0) {
                    mapper = new IrregularOffsetMapper(start);
                } else {
                    mapper = new OffsetMapper();
                }
                
                return numEntries;
            }
        }
        throw new ZipException(
                "Not a ZIP compatible file: End Of Central Directory signature is missing!");
    }

    /**
     * A factory method returning a newly created ZipEntry for the given name.
     */
    protected ZipEntry createZipEntry(String name) {
        return new ZipEntry(name);
    }

    /**
     * Returns the comment of this ZIP compatible file or <code>null</code>
     * if no comment exists.
     */
    public String getComment() {
        return comment;
    }
    
    private void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Returns <code>true</code> if and only if some input streams are open to
     * read from this ZIP compatible file.
     */
    public synchronized boolean busy() {
        return openStreams > 0;
    }

    /**
     * Returns the encoding to use for filenames and the file comment.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Returns an enumeration of the ZIP entries in this ZIP file.
     */
    public Enumeration entries() {
        return Collections.enumeration(entries.values());
    }

    /**
     * Returns the {@link ZipEntry} for the given name or <code>null</code> if no
     * entry with that name exists.
     *
     * @param name Name of the ZIP entry.
     */
    public ZipEntry getEntry(String name) {
        return (ZipEntry) entries.get(name);
    }

    /**
     * Returns the number of entries in this ZIP compatible file.
     */
    public int size() {
	return entries.size();
    }

    /**
     * Returns the file length of this ZIP compatible file in bytes.
     */
    public long length() throws IOException {
        return archive.length();
    }

    /**
     * Returns the length of the preamble of this ZIP compatible file in bytes.
     *
     * @return A positive value or zero to indicate that this ZIP compatible
     *         file does not have a preamble.
     *
     * @since TrueZIP 5.1
     */
    public long getPreambleLength() {
        return preamble;
    }
    
    /**
     * Returns an {@link InputStream} to read the preamble of this ZIP
     * compatible file.
     * <p>
     * Note that the returned stream is a <i>lightweight</i> stream,
     * i.e. there is no external resource such as a {@link ReadOnlyFile}
     * allocated for it. Instead, all streams returned by this method share
     * the underlying <code>ReadOnlyFile</code> of this <code>ZipFile</code>.
     * This allows to close this object (and hence the underlying
     * <code>ReadOnlyFile</code>) without cooperation of the returned
     * streams, which is important if the application wants to work on the
     * underlying file again (e.g. update or delete it).
     *
     * @since TrueZIP 5.1
     */
    public InputStream getPreambleInputStream() throws IOException {
        return new BoundedInputStream(0, preamble);
    }

    /**
     * Returns the length of the postamble of this ZIP compatible file in bytes.
     *
     * @return A positive value or zero to indicate that this ZIP compatible
     *         file does not have an postamble.
     *
     * @since TrueZIP 5.1
     */
    public long getPostambleLength() {
        return postamble;
    }
    
    /**
     * Returns an {@link InputStream} to read the postamble of this ZIP
     * compatible file.
     * <p>
     * Note that the returned stream is a <i>lightweight</i> stream,
     * i.e. there is no external resource such as a {@link ReadOnlyFile}
     * allocated for it. Instead, all streams returned by this method share
     * the underlying <code>ReadOnlyFile</code> of this <code>ZipFile</code>.
     * This allows to close this object (and hence the underlying
     * <code>ReadOnlyFile</code>) without cooperation of the returned
     * streams, which is important if the application wants to work on the
     * underlying file again (e.g. update or delete it).
     *
     * @since TrueZIP 5.1
     */
    public InputStream getPostambleInputStream() throws IOException {
        return new BoundedInputStream(archive.length() - postamble, postamble);
    }

    /**
     * Returns <code>true</code> if and only if the offsets in this ZIP file
     * are relative to the start of the file, rather than the first Local
     * File Header.
     * <p>
     * This method is intended for very special purposes only.
     */
    public boolean offsetsConsiderPreamble() {
        assert mapper != null;
        return mapper.location(0) == 0;
    }

    /**
     * Equivalent to {@link #getCheckedInputStream(String)
     * getCheckedInputStream(entry.getName())}.
     *
     * @since TrueZIP 6.0
     */
    public final InputStream getCheckedInputStream(final ZipEntry entry)
    throws IOException {
        return getCheckedInputStream(entry.getName());
    }

    /**
     * Equivalent to {@link #getInputStream(String, boolean)
     * getInputStream(name, true)}, but also checks the CRC-32 checksum.
     * <p>
     * If there is a mismatch of the CRC-32 values for the ZIP entry,
     * the <code>close()</code> method of the returned input stream will
     * throw a {@link CRC32Exception}.
     * Other than this, the archive entry will be processed normally.
     * So if just the CRC-32 value for the entry in the archive file has been
     * modified, you can still read its contents.
     *
     * @param name The name of the entry to get the stream for
     *        - may <em>not</em> be <code>null</code>!
     *
     * @return A stream to read the entry data from or <code>null</code> if the
     *         entry does not exist.
     *
     * @throws NullPointerException If <code>name</code> is <code>null</code>.
     * @throws IOException If the entry cannot get read from this ZipFile.
     *
     * @since TrueZIP 6.1
     */
    public InputStream getCheckedInputStream(final String name)
    throws IOException {
        if (name == null)
            throw new NullPointerException();
        final ZipEntry entry = (ZipEntry) entries.get(name);
        final InputStream in = getInputStreamImpl(entry, true);
        if (in == null)
            return null;

        return new CheckedInputStream(in, entry);
    }

    private static class CheckedInputStream extends FilterInputStream {
        private final CRC32 crc = new CRC32();
        private final ZipEntry entry;

        public CheckedInputStream(InputStream in, ZipEntry entry) {
            super(in);
            this.entry = entry;
        }

        public int read() throws IOException {
            int b = super.read();
            if (b != -1)
                crc.update(b);
            return b;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            final int n = super.read(b, off, len);
            if (n != -1)
                crc.update(b, off, n);
            return n;
        }

        public long skip(final long n) throws IOException {
            if (n <= 0 || available() <= 0) // TODO: Review: This works on non-blocking input streams only!
                return 0;

            long rem = n;
            int max = (int) Math.min(FLATER_BUF_LENGTH, rem);
            final byte[] buf = new byte[max];

            while (rem > 0) {
                max = (int) Math.min(FLATER_BUF_LENGTH, rem);
                int read = super.read(buf, 0, max);
                if (read == -1)
                    break;
                crc.update(buf, 0, read);
                rem -= read;
            }
            return n - rem;
        }

        public void close() throws IOException {
            try {
                skip(Long.MAX_VALUE); // process CRC until EOF
            } finally {
                super.close();
            }
            long expectedCrc = entry.getCrc();
            long actualCrc = crc.getValue();
            if (expectedCrc != actualCrc)
                throw new CRC32Exception(
                        entry.getName(), expectedCrc, actualCrc);
        }
    };

    /**
     * Equivalent to {@link #getInputStream(String, boolean)
     * getInputStream(entry.getName(), true)}.
     */
    public final InputStream getInputStream(ZipEntry entry)
    throws IOException {
        return getInputStream(entry.getName(), true);
    }

    /**
     * Equivalent to {@link #getInputStream(String, boolean)
     * getInputStream(entry.getName(), inflate)}.
     */
    public final InputStream getInputStream(ZipEntry entry, boolean inflate)
    throws IOException {
        return getInputStream(entry.getName(), inflate);
    }
    
    /**
     * Equivalent to {@link #getInputStream(String, boolean)
     * getInputStream(name, true)}.
     */
    public final InputStream getInputStream(String name)
    throws IOException {
        return getInputStream(name, true);
    }
    
    /**
     * Returns an {@link InputStream} for reading the contents of the given
     * entry.
     * <p>
     * The returned stream is a <i>lightweight</i> stream,
     * i.e. there is no external resource such as a {@link ReadOnlyFile}
     * allocated for it. Instead, all streams returned by this method share
     * the underlying <code>ReadOnlyFile</code> of this <code>ZipFile</code>.
     * This allows to close this object (and hence the underlying
     * <code>ReadOnlyFile</code>) without cooperation of the returned
     * streams, which is important if the application wants to work on the
     * underlying file again (e.g. update or delete it).
     * <p>
     * For performance reasons, the CRC-32 checksum of the entry is
     * <em>not</em> validated.
     * If you need to validate the CRC-32 checksum, you could either use the
     * <code>crc32</code> property of the provided entry directly or call
     * {@link #getCheckedInputStream} instead.
     *
     * @param name The name of the entry to get the stream for
     *        - may <em>not</em> be <code>null</code>!
     * @param inflate Whether or not the entry data should be inflated.
     *        Use this to directly read deflated data only!
     *
     * @return A stream to read the entry data from or <code>null</code> if the
     *         entry does not exist.
     *
     * @throws NullPointerException If <code>name</code> is <code>null</code>.
     * @throws IOException If the entry cannot get read from this ZipFile.
     */
    public synchronized InputStream getInputStream(
            final String name,
            final boolean inflate)
    throws  IOException {
        if (name == null)
            throw new NullPointerException();
        final ZipEntry entry = (ZipEntry) entries.get(name);
        return getInputStreamImpl(entry, inflate);
    }

    private final InputStream getInputStreamImpl(
            final ZipEntry entry,
            final boolean inflate)
    throws  ZipException,
            IOException {
        ensureOpen();
        if (entry == null)
            return null;

        final String name = entry.getName();

        // Double checking is not guaranteed to work with the long primitive type.
        long offset;
        /*offset = entry.offset;
        assert offset != -1;
        if (offset < 0) {*/
            synchronized (entry) {
                offset = entry.offset;
                assert offset != -1;
                if (offset < 0) {
                    // This offset has been set by loadCentralDirectory()
                    // and needs to be resolved first.
                    offset &= ~LONG_MSB; // Switch off MSB.
                    archive.seek(offset);
                    final byte[] lfh = new byte[LFH_MIN_LEN];
                    archive.readFully(lfh);
                    final long lfhSig = readUInt(lfh);
                    if (lfhSig != LFH_SIG)
                        throw new ZipException(name + ": Local File Header signature expected!");
                    offset += LFH_MIN_LEN
                            + readUShort(lfh, LFH_FILE_NAME_LENGTH_OFFSET) // file name length
                            + readUShort(lfh, LFH_FILE_NAME_LENGTH_OFFSET + 2); // extra field length
                    entry.offset = offset;
                }
            }
        //}

        final BoundedInputStream bis
                = new BoundedInputStream(offset, entry.getCompressedSize());
        final InputStream in;
        switch (entry.getMethod()) {
            case ZipEntry.DEFLATED:
                if (inflate) {
                    bis.addDummy();
                    long size = entry.getSize();
                    if (size > FLATER_BUF_LENGTH)
                        size = FLATER_BUF_LENGTH;
                    else if (size < FLATER_BUF_LENGTH / 8)
                        size = FLATER_BUF_LENGTH / 8;
                    in = new PooledInflaterInputStream(bis, (int) size);
                    break;
                }
                // Fall through.

            case ZipEntry.STORED:
                in = bis;
                break;

            default:
                throw new ZipException(name + ": " + entry.getMethod()
                        + ": Unsupported compression method!");
        }

        return in;
    }

    /**
     * Ensures that this archive is still open. This <em>must</em> be
     * protected by a synchronization lock on the enclosing
     * <code>ZipFile</code> object.
     */
    private final void ensureOpen() throws ZipException {
        if (archive == null)
            throw new ZipException("ZipFile has been closed!");
    }

    private static class PooledInflaterInputStream extends InflaterInputStream {
        private boolean closed;

        public PooledInflaterInputStream(InputStream in, int size) {
            super(in, allocateInflater(), size);
        }

        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    super.close();
                } finally {
                    releaseInflater(inf);
                }
            }
        }
    };

    private static Inflater allocateInflater() {
        Inflater inflater = null;

        synchronized (releasedInflaters) {
            for (Iterator i = releasedInflaters.iterator(); i.hasNext(); ) {
                inflater = (Inflater) ((Reference) i.next()).get();
                i.remove();
                if (inflater != null) {
                    inflater.reset();
                    break;
                }
            }
            if (inflater == null)
                inflater = new Inflater(true);

            // We MUST make sure that we keep a strong reference to the
            // inflater in order to retain it from being released again and
            // then finalized when the close() method of the InputStream
            // returned by getInputStream(...) is called from within another
            // finalizer.
            // The finalizer of the inflater calls end() and leaves the object
            // in a state so that the subsequent call to reset() throws an NPE.
            // The ZipFile class in Sun's J2SE 1.4.2 shows this bug.
            allocatedInflaters.add(inflater);
        }
        
        return inflater;
    }

    private static void releaseInflater(Inflater inflater) {
        synchronized (releasedInflaters) {
            releasedInflaters.add(new SoftReference(inflater));
            allocatedInflaters.remove(inflater);
        }
    }

    private static final Set allocatedInflaters = new HashSet();
    private static final List releasedInflaters = new LinkedList();

    /**
     * Closes the file.
     * This closes any open input streams reading from this ZIP file.
     * 
     * @throws IOException if an error occurs closing the file.
     */
    public synchronized void close() throws IOException {
        // Order is important here!
        if (archive != null) {
            final ReadOnlyFile oldArchive = archive;
            archive = null;
            oldArchive.close();
        }
    }

    private static final int readUShort(final byte[] bytes) {
        return readUShort(bytes, 0);
    }

    private static final int readUShort(final byte[] bytes, final int off) {
        return ((bytes[off + 1] & 0xFF) << 8) | (bytes[off] & 0xFF);
    }

    private static final long readUInt(final byte[] bytes) {
        return readUInt(bytes, 0);
    }
    
    private static final long readUInt(final byte[] bytes, int off) {
        off += 3;
        long v = bytes[off--] & 0xFFl;
        v <<= 8;
        v |= bytes[off--] & 0xFFl;
        v <<= 8;
        v |= bytes[off--] & 0xFFl;
        v <<= 8;
        v |= bytes[off] & 0xFFl;
        return v;
    }

    /**
     * InputStream that delegates requests to the underlying
     * RandomAccessFile, making sure that only bytes from a certain
     * range can be read.
     * This design of this class makes the ZipFile class thread safe,
     * i.e. multiple threads may safely retrieve individual InputStreams.
     * It also allows to call close() on the ZipFile, thereby closing all
     * input streams reading from it, which is important in the context of
     * TrueZIP's high level API.
     */
    private class BoundedInputStream extends AccountedInputStream {
        private long remaining;
        private long fp;
        private boolean addDummyByte;

        /**
         * @param start The start address (not offset) in <code>archive</code>.
         * @param remaining The remaining bytes allowed to be read in
         *        <code>archive</code>.
         */
        BoundedInputStream(long start, long remaining) {
            assert start >= 0;
            assert remaining >= 0;
            this.remaining = remaining;
            fp = start;
        }

        public int read() throws IOException {
            if (remaining <= 0) {
                if (addDummyByte) {
                    addDummyByte = false;
                    return 0;
                }

                return -1;
            }
            

            final int ret;
            synchronized (ZipFile.this) {
                ensureOpen();
                archive.seek(fp);
                ret = archive.read();
            }
            if (ret >= 0) {
                fp++;
                remaining--;
            }

            return ret;
        }

        public int read(final byte[] b, final int off, int len)
        throws IOException {
            if (len <= 0)
                return 0;
            
            if (remaining <= 0) {
                if (addDummyByte) {
                    addDummyByte = false;
                    b[off] = 0;
                    return 1;
                }

                return -1;
            }

            if (len > remaining)
                len = (int) remaining;
            

            final int ret;
            synchronized (ZipFile.this) {
                ensureOpen();
                archive.seek(fp);
                ret = archive.read(b, off, len);
            }
            if (ret > 0) {
                fp += ret;
                remaining -= ret;
            }

            return ret;
        }

        /**
         * Inflater needs an extra dummy byte for nowrap - see
         * Inflater's javadocs.
         */
        void addDummy() {
            addDummyByte = true;
        }

        /**
         * @return The number of bytes remaining in this entry, yet maximum
         *         <code>Integer.MAX_VALUE</code>.
         *         Note that this is only relevant for entries which have been
         *         stored with the <code>STORED</code> method.
         *         For entries stored according to the <code>DEFLATED</code>
         *         method, the value returned by this method on the
         *         <code>InputStream</code> returned by {@link #getInputStream}
         *         is actually determined by an {@link InflaterInputStream}.
         */
        public int available() {
            long available = remaining;
            if (addDummyByte)
                available++;
            return available > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) available;
        }
    } // class BoundedInputStream

    private abstract class AccountedInputStream extends InputStream {
        private boolean closed;

        public AccountedInputStream() {
            synchronized (ZipFile.this) {
                openStreams++;
            }
        }

        public void close() throws IOException {
            synchronized (ZipFile.this) {
                // Order is important here!
                if (!closed) {
                    closed = true;
                    openStreams--;
                    super.close();
                }
            }
        }

        protected void finalize() throws IOException {
            close();
        }
    };

    private static class OffsetMapper {
        public long location(long offset) {
            return offset;
        }
    }
    
    private static class IrregularOffsetMapper extends OffsetMapper {
        final long start;

        public IrregularOffsetMapper(long start) {
            this.start = start;
        }

        public long location(long offset) {
            return start + offset;
        }
    }
}