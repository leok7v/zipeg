/*
 * Copyright 2005-2006 Schlichtherle IT Services
 * Copyright 2001-2005 The Apache Software Foundation
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

import de.schlichtherle.io.util.LEDataOutputStream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

/**
 * Replacement for
 * {@link java.util.zip.ZipOutputStream java.util.zip.ZipOutputStream}.
 * <p>
 * This class is designed to be thread safe.
 */
public class ZipOutputStream extends FilterOutputStream implements ZipConstants {

    /**
     * The file comment.
     */
    private String comment = "";

    /**
     * Default compression method for next entry.
     */
    private short method = ZipEntry.DEFLATED;

    /**
     * The list of ZIP entries started to be written so far.
     * Maps entry names to zip entries [{@link String} -> {@link ZipEntry}].
     */
    private final Map entries = new LinkedHashMap();

    /**
     * Start of entry data.
     */
    private long dataStart;

    /**
     * Start of central directory.
     */
    private long cdOffset;

    /**
     * Length of central directory.
     */
    private long cdLength;

    /**
     * The encoding to use for entry names and comments.
     */
    private final String encoding;

    private boolean finished;

    private boolean closed;

    /**
     * Current entry.
     */
    private ZipEntry entry;
    
    /**
     * Whether or not we need to deflate the current entry.
     * This can be used together with the DEFLATED method to write already
     * compressed entry data into the ZIP file.
     */
    private boolean deflate;// = true;

    /**
     * CRC instance to avoid parsing DEFLATED data twice.
     */
    private final CRC32 crc = new CRC32();

    /**
     * This Deflater object is used for deflated output.
     * This is actually an instance of the class {@link ZipDeflater}.
     */
    private final Deflater def = new ZipDeflater();

    /**
     * This buffer holds deflated data for output.
     */
    private final byte[] dbuf = new byte[FLATER_BUF_LENGTH];
    
    /**
     * Creates a new ZIP output stream decorating the given output stream,
     * using the {@link #DEFAULT_ENCODING}.
     *
     * @throws NullPointerException If <code>out</code> is <code>null</code>.
     */
    public ZipOutputStream(
            final OutputStream out)
    throws NullPointerException {
        super(toLEDataOutputStream(out));

        // Check parameters (fail fast).
        if (out == null)
            throw new NullPointerException("out");

        this.encoding = DEFAULT_ENCODING;
    }

    /**
     * Creates a new ZIP output stream decorating the given output stream.
     *
     * @throws NullPointerException If <code>out</code> or <code>encoding</code> is
     *         <code>null</code>.
     * @throws UnsupportedEncodingException If encoding is not supported by
     *         this JVM.
     */
    public ZipOutputStream(
            final OutputStream out,
            final String encoding)
    throws  NullPointerException,
            UnsupportedEncodingException {
        super(toLEDataOutputStream(out));

        // Check parameters (fail fast).
        if (out == null)
            throw new NullPointerException("out");
        if (encoding == null)
            throw new NullPointerException("encoding");
        "".getBytes(encoding); // may throw UnsupportedEncodingException!

        this.encoding = encoding;
    }

    private static LEDataOutputStream toLEDataOutputStream(OutputStream out) {
        return out instanceof LEDataOutputStream
                ? (LEDataOutputStream) out
                : new LEDataOutputStream(out);
    }

    /**
     * Returns the encoding to use for filenames and the file comment.
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Returns the number of ZIP entries written so far.
     */
    public synchronized int size() {
        return entries.size();
    }

    /**
     * Returns an enumeration of the ZIP entries written so far.
     */
    public synchronized Enumeration entries() {
        return Collections.enumeration(entries.values());
    }

    /**
     * Returns the {@link ZipEntry} for the given name or <code>null</code> if no
     * entry with that name has been written so far.
     *
     * @param name Name of the ZIP entry.
     */
    public synchronized ZipEntry getEntry(String name) {
        return (ZipEntry) entries.get(name);
    }

    /**
     * Set the file comment.
     */
    public synchronized void setComment(String comment) {
        this.comment = comment;
    }

    public synchronized String getComment() {
        return comment;
    }
    
    /**
     * Sets the compression level for subsequent entries.
     */
    public synchronized void setLevel(int level) {
	def.setLevel(level);
    }

    /**
     * Returns the compression level currently used.
     */
    public synchronized int getLevel() {
        return ((ZipDeflater) def).getLevel();
    }

    /**
     * Sets the default compression method for subsequent entries.
     * 
     * <p>Default is DEFLATED.</p>
     */
    public synchronized void setMethod(short method) {
	if (method != STORED && method != DEFLATED)
	    throw new IllegalArgumentException("Invalid compression method!");
        this.method = method;
    }

    public synchronized short getMethod() {
        return method;
    }

    /**
     * Returns the total number of (compressed) bytes this stream has written
     * to the underlying stream.
     */
    public synchronized long length() {
        return ((LEDataOutputStream) out).size();
    }

    /**
     * Returns <code>true</code> if and only if this
     * <code>ZipOutputStream</code> is currently writing a ZIP entry.
     */
    public synchronized final boolean busy() {
        return entry != null;
    }

    /**
     * Equivalent to
     * {@link #putNextEntry(ZipEntry, boolean) putNextEntry(ze, true)}.
     */
    public final void putNextEntry(final ZipEntry ze)
    throws IOException {
        putNextEntry(ze, true);
    }
    
    /**
     * Starts writing the next ZIP entry to the underlying stream.
     * Note that if two or more entries with the same name are written
     * consecutively to this stream, the last entry written will shadow
     * all other entries, i.e. all of them are written to the ZIP compatible
     * file (and hence require space), but only the last will be accessible
     * from the central directory.
     * This is unlike the genuine {@link java.util.zip.ZipOutputStream
     * java.util.zip.ZipOutputStream} which would throw a {@link ZipException}
     * in this method when the second entry with the same name is to be written.
     *
     * @param ze The ZIP entry to write.
     * @param deflate Whether or not the entry data should be deflated.
     *        Use this to directly write already deflated data only!
     *
     * @throws ZipException If and only if writing the entry is impossible
     *         because the resulting file would not comply to the ZIP file
     *         format specification.
     * @throws IOException On any I/O related issue.
     */
    public synchronized void putNextEntry(final ZipEntry ze, final boolean deflate)
    throws IOException {
        closeEntry();

        final String name = ze.getName();
        /*if (entries.get(name) != null)
            throw new ZipException(name + ": Duplicate entry!");*/

        if (ze.getMethod() == -1) // not specified
            ze.setMethod(getMethod());
        if (ze.getTime() == -1) // not specified
            ze.setTime(System.currentTimeMillis());
        
        // check sum of name, extra and comment size
        int size = name.getBytes(encoding).length;
        final byte[] extra = ze.getExtra();
        if (extra != null)
            size += extra.length;
        final String comment = ze.getComment();
        if (comment != null)
            size += comment.getBytes(encoding).length;
        if (size > 0xFFFF)
            throw new ZipException(
                    "Sum of entry name, extra fields and comment too long (max " + 0xFFFF + "): " + size);
        
        switch (ze.getMethod()) {
            case ZipEntry.STORED:
                {
                    final String s = " is required for STORED method!";
                    if (ze.getCrc() == -1)
                        throw new ZipException("CRC checksum" + s);
                    if (ze.getSize() == -1)
                        throw new ZipException("Uncompressed size" + s);
                    ze.setCompressedSize(ze.getSize());
                }
                this.deflate = false;
                break;
                
            case ZipEntry.DEFLATED:
                if (!deflate) {
                    final String s = " is required for DEFLATED method when writing raw deflated data!";
                    if (ze.getCrc() == -1)
                        throw new ZipException("CRC checksum" + s);
                    if (ze.getCompressedSize() == -1)
                        throw new ZipException("Compressed size" + s);
                    if (ze.getSize() == -1)
                        throw new ZipException("Uncompressed size" + s);
                }
                this.deflate = deflate;
                break;
                
            default:
                throw new ZipException(
                        "Unsupported compression method: " + ze.getMethod());
        }

        finished = false;

        // Write LFH BEFORE putting the entry in the map.
        entry = ze;
        writeLocalFileHeader();

        // Store entry now so immediate subsequent call to getEntry(...)
        // returns it.
        final ZipEntry old = (ZipEntry) entries.put(name, ze);
        assert old == null;
    }

    /**
     * @throws IOException On any I/O related issue.
     */
    private void writeLocalFileHeader() throws IOException {
        final ZipEntry entry = this.entry;
        assert entry != null;

        final LEDataOutputStream dos = (LEDataOutputStream) out;

        entry.offset = dos.size();

        dos.writeInt(LFH_SIG);

        // version needed to extract
        // general purpose bit flag
        final boolean useDD = entry.getMethod() == DEFLATED;
        if (useDD) {
            // Version 2.0 required to extract.
            dos.writeShort(20);
            // Bit 3 set to signal that we use a data descriptor
            dos.writeShort(8);
        } else {
            dos.writeShort(10);
            dos.writeShort(0);
        }

        // Compression method.
        dos.writeShort(entry.getMethod());

        // Last modification time and date in DOS format.
        dos.writeInt((int) entry.getDosTime());

        // CRC32.
        // Compressed length.
        // Uncompressed length.
        if (useDD) {
            dos.writeInt(0);
            dos.writeInt(0);
            dos.writeInt(0);
        } else {
            dos.writeInt((int) entry.getCrc());
            dos.writeInt((int) entry.getCompressedSize());
            dos.writeInt((int) entry.getSize());
        }

        // file name length
        final byte[] name = entry.getName().getBytes(encoding);
        dos.writeShort(name.length);

        // extra field length
        byte[] extra = entry.getExtra();
        if (extra == null)
            extra = new byte[0];
        dos.writeShort(extra.length);

        // file name
        dos.write(name);

        // extra field
        dos.write(extra);

        dataStart = dos.size();
    }

    /**
     * @throws IOException On any I/O related issue.
     */
    public synchronized void write(int b) throws IOException {
        // Although it is inefficient, the use of a local buffer enables
        // thread-safety.
        byte[] buf = new byte[1];
        buf[0] = (byte) b;
        write(buf, 0, 1);
    }
    
    /**
     * @throws IOException On any I/O related issue.
     */
    public synchronized void write(final byte[] b, final int off, final int len)
    throws IOException {
        if (busy()) {
            if (len <= 0)
                return;
            if (deflate) {
                // Fast implementation.
                assert !def.finished();
                def.setInput(b, off, len);
                while (!def.needsInput())
                    deflate();
                crc.update(b, off, len);
            } else {
                out.write(b, off, len);
                if (entry.getMethod() != DEFLATED)
                    crc.update(b, off, len);
            }
        } else {
            out.write(b, off, len);
        }
    }

    /**
     * @throws IOException If there is no current ZIP entry to write.
     */
    /*private final void ensureEntry() throws IOException {
        if (!busy())
            throw new IOException("There is no current ZIP entry to write!");
    }*/

    private final void deflate() throws IOException {
        final int dlen = def.deflate(dbuf, 0, dbuf.length);
        if (dlen > 0)
            out.write(dbuf, 0, dlen);
    }

    /**
     * Writes all necessary data for this entry to the underlying stream.
     *
     * @throws ZipException If and only if writing the entry is impossible
     *         because the resulting file would not comply to the ZIP file
     *         format specification.
     * @throws IOException On any I/O related issue.
     */
    public synchronized void closeEntry() throws IOException {
        if (entry == null)
            return;

        switch (entry.getMethod()) {
            case ZipEntry.STORED:
                final long expectedCrc = crc.getValue();
                if (entry.getCrc() != expectedCrc) {
                    throw new ZipException("Bad CRC checksum for entry "
                                           + entry.getName() + ": "
                                           + Long.toHexString(entry.getCrc())
                                           + " instead of "
                                           + Long.toHexString(expectedCrc));
                }
                final long written = ((LEDataOutputStream) out).size();
                if (entry.getSize() != written - dataStart) {
                    throw new ZipException("Bad size for entry "
                                           + entry.getName() + ": "
                                           + entry.getSize()
                                           + " instead of "
                                           + (written - dataStart));
                }
                break;
                
            case ZipEntry.DEFLATED:
                if (deflate) {
                    assert !def.finished();
                    def.finish();
                    while (!def.finished())
                        deflate();

                    entry.setCrc(crc.getValue());
                    entry.setCompressedSize(def.getTotalOut() & 0xFFFFFFFFl);
                    entry.setSize(def.getTotalIn() & 0xFFFFFFFFl);

                    def.reset();
                } else {
                    // Note: There is no way to check whether the written
                    // data matches the crc, the compressed size and the
                    // uncompressed size!
                }
                break;
                
            default:
                throw new ZipException(
                        "Unsupported compression method: " + entry.getMethod());
        }

        writeDataDescriptor();
        flush();
        crc.reset();
        entry = null;
    }

    /**
     * @throws IOException On any I/O related issue.
     */
    private void writeDataDescriptor() throws IOException {
        final ZipEntry entry = this.entry;
        assert entry != null;

        if (entry.getMethod() == STORED)
            return;

        final LEDataOutputStream dos = (LEDataOutputStream) out;

        dos.writeInt(DD_SIG);
        dos.writeInt((int) entry.getCrc());
        dos.writeInt((int) entry.getCompressedSize());
        dos.writeInt((int) entry.getSize());
    }

    /**
     * Closes the current entry and writes the central directory to the
     * underlying output stream.
     * <p>
     * <b>Notes:</b>
     * <ul>
     * <li>The underlying stream is not closed.</li>
     * <li>Unlike Sun's implementation in J2SE 1.4.2, you may continue to use
     *     this ZIP output stream with putNextEntry(...) and the like.
     *     When you finally close the stream, the central directory will
     *     contain <em>all</em> entries written.</li>
     * </ul>
     *
     * @throws ZipException If and only if writing the entry is impossible
     *         because the resulting file would not comply to the ZIP file
     *         format specification.
     * @throws IOException On any I/O related issue.
     */
    public synchronized void finish() throws IOException {
        if (finished)
            return;

        // Order is important here!
        finished = true;

        closeEntry();
        final LEDataOutputStream dos = (LEDataOutputStream) out;
        cdOffset = dos.size();
        final Iterator i = entries.values().iterator();
        while (i.hasNext())
            writeCentralFileHeader((ZipEntry) i.next());
        cdLength = dos.size() - cdOffset;
        writeEndOfCentralDirectory();
    }

    /**
     * Writes the central file header entry
     *
     * @throws IOException On any I/O related issue.
     */
    private void writeCentralFileHeader(final ZipEntry ze) throws IOException {
        assert ze != null;

        final LEDataOutputStream dos = (LEDataOutputStream) out;

        dos.writeInt(CFH_SIG);

        // version made by
        dos.writeShort((ze.getPlatform() << 8) | 20);

        // version needed to extract
        // general purpose bit flag
        if (ze.getMethod() == DEFLATED) {
            // requires version 2 as we are going to store length info
            // in the data descriptor
            dos.writeShort(20);

            // bit3 set to signal, we use a data descriptor
            dos.writeShort(8);
        } else {
            dos.writeShort(10);
            dos.writeShort(0);
        }

        // compression method
        dos.writeShort(ze.getMethod());

        // last mod. time and date
        dos.writeInt((int) ze.getDosTime());

        // CRC
        // compressed length
        // uncompressed length
        dos.writeInt((int) ze.getCrc());
        dos.writeInt((int) ze.getCompressedSize());
        dos.writeInt((int) ze.getSize());

        // file name length
        final byte[] name = ze.getName().getBytes(encoding);
        dos.writeShort(name.length);

        // extra field length
        byte[] extra = ze.getExtra();
        if (extra == null)
            extra = new byte[0];
        dos.writeShort(extra.length);

        // file comment length
        String comment = ze.getComment();
        if (comment == null)
            comment = "";
        final byte[] data = comment.getBytes(encoding);
        dos.writeShort(data.length);

        // disk number start
        dos.writeShort(0);

        // internal file attributes
        dos.writeShort(0);

        // external file attributes
        dos.writeInt(0);

        // relative offset of local file header
        dos.writeInt((int) ze.offset);

        // file name
        dos.write(name);

        // extra field
        dos.write(extra);

        // file comment
        dos.write(data);
    }

    /**
     * Writes the &quot;End of central dir record&quot;
     *
     * @throws IOException On any I/O related issue.
     */
    private void writeEndOfCentralDirectory() throws IOException {
        final LEDataOutputStream dos = (LEDataOutputStream) out;

        dos.writeInt(EOCD_SIG);

        // disk numbers
        dos.writeShort(0);
        dos.writeShort(0);

        // number of entries
        dos.writeShort(entries.size());
        dos.writeShort(entries.size());

        // length and location of CD
        dos.writeInt((int) cdLength);
        dos.writeInt((int) cdOffset);

        // ZIP file comment
        String comment = getComment();
        if (comment == null)
            comment = "";
        byte[] data = comment.getBytes(encoding);
        dos.writeShort(data.length);
        dos.write(data);
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with the stream.
     * This closes the open output stream writing to this ZIP file,
     * if any.
     *
     * @throws IOException On any I/O related issue.
     */
    public synchronized void close() throws IOException {
        if (closed)
            return;

        // Order is important here!
        closed = true;

        try {
            finish();
        } finally {
            entries.clear();
            super.close();
        }
    }

    /**
     * A Deflater which can be asked for its current deflation level.
     */
    private static class ZipDeflater extends Deflater {
        private int level = Deflater.DEFAULT_COMPRESSION;
        
        public ZipDeflater() {
            super(Deflater.DEFAULT_COMPRESSION, true);
        }
        
        public int getLevel() {
            return level;
        }
        
        public void setLevel(int level) {
            super.setLevel(level);
            this.level = level;
        }
    }
}
