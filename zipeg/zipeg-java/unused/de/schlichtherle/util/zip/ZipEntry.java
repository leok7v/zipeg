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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.zip.ZipException;

/**
 * Replacement for {@link java.util.zip.ZipEntry}.
 * <p>
 * Note that a <code>ZipEntry</code> object can be used with only one
 * {@link ZipFile} or {@link ZipOutputStream} object.
 * Reusing the same <code>ZipEntry</code> object with a second object of these
 * classes is an error and may result in unpredictable behaviour.
 */
public class ZipEntry implements ZipConstants, Cloneable {
    private String name;
    private short platform = PLATFORM_FAT;  // 2 bytes unsigned int
    private short method = -1;              // 2 bytes unsigned int
    private long time = -1;                 // dos time as 4 bytes unsigned int
    private long crc = -1;                  // 4 bytes unsigned int
    private long csize = -1;                // 4 bytes unsigned int
    private long size = -1;                 // 4 bytes unsigned int
    private byte[] extra;                   // null if no extra field
    private String comment;                 // null if no comment field

    /** Meaning depends on using class. */
    long offset = -1;

    /**
     * Creates a new zip entry with the specified name.
     */
    public ZipEntry(String name) {
	if (name == null)
	    throw new NullPointerException();
	if (name.length() > 0xFFFF)
	    throw new IllegalArgumentException("Entry name too long!");
        this.name = name;
    }

    /**
     * Creates a new zip entry with fields taken from the specified zip entry.
     */
    public ZipEntry(ZipEntry entry) {
	name = entry.name;
	method = entry.method;
	time = entry.time;
	crc = entry.crc;
	size = entry.size;
	csize = entry.csize;
	extra = entry.extra;
	comment = entry.comment;
        offset = entry.offset;
    }

    /**
     * Overwrite clone.
     */
    public Object clone() {
	try {
	    final ZipEntry entry = (ZipEntry) super.clone();
	    entry.extra = extra != null ? (byte[]) extra.clone() : null;
	    return entry;
	}
        catch (CloneNotSupportedException cannotHappen) {
	    throw new AssertionError(cannotHappen); // never say never
	}
    }

    /** Returns the ZIP entry name. */
    public String getName() {
	return name;
    }

    /**
     * Sets the ZIP entry name.
     *
     * @since TrueZIP 6.0
     */
    protected void setName(String name) {
        this.name = name;
    }

    /**
     * Returns true if and only if this ZIP entry represents a directory entry
     * (i.e. end with <code>'/'</code>).
     */
    public boolean isDirectory() {
	return name.endsWith("/");
    }

    public short getPlatform() {
	return platform;
    }

    public void setPlatform(short platform) {
        this.platform = platform;
    }

    public short getMethod() {
	return method;
    }

    public void setMethod(short method) {
	if (method != STORED && method != DEFLATED)
	    throw new IllegalArgumentException(name
                    + ": Invalid entry compression method!");
        this.method = method;
    }

    protected long getDosTime() {
	return time;
    }

    protected void setDosTime(long time) {
        if (time < 0)
            throw new IllegalArgumentException(name
                    + ": Invalid entry modification time!");
	this.time = time;
    }

    public long getTime() {
	return time != -1 ? dos2javaTime(time) : -1;
    }

    public void setTime(long time) {
        if (time < 0)
            throw new IllegalArgumentException(name
                    + ": Invalid entry modification time!");
	this.time = time != -1 ? java2dosTime(time) : -1;
    }

    public long getCrc() {
	return crc;
    }

    public void setCrc(long crc) {
	if (crc < 0 || 0xFFFFFFFFl < crc)
	    throw new IllegalArgumentException(name
                    + ": Invalid entry crc!");
	this.crc = crc;
    }

    public long getCompressedSize() {
	return csize;
    }

    public void setCompressedSize(long csize) {
	if (csize < 0 || 0xFFFFFFFFl < csize)
	    throw new IllegalArgumentException(name
                    + ": Invalid entry compressed size!");
	this.csize = csize;
    }

    public long getSize() {
	return size;
    }

    public void setSize(long size) {
	if (size < 0 || 0xFFFFFFFFl < size)
	    throw new IllegalArgumentException(name
                    + ": Invalid entry size!");
	this.size = size;
    }

    public byte[] getExtra() {
	return extra != null ? (byte[]) extra.clone() : null;
    }

    public void setExtra(byte[] extra) {
	if (extra != null && 0xFFFF < extra.length)
	    throw new IllegalArgumentException(name
                    + ": Invalid entry extra field length!");
	this.extra = extra;
    }

    public String getComment() {
	return comment;
    }

    public void setComment(String comment) {
	if (comment != null && 0xFFFF < comment.length())
	    throw new IllegalArgumentException(name
                    + ": Invalid entry comment length!");
	this.comment = comment;
    }

    /**
     * Returns the ZIP entry name.
     */
    public String toString() {
	return getName();
    }

    //
    // Time conversion.
    //

    /**
     * Converts a DOS date/time field to unix time.
     *
     * @param dosTime Contains the DOS date/time field.
     *
     * @return The number of milliseconds from the epoch.
     */
    protected static long dos2javaTime(long dosTime) {
        final Calendar cal = (Calendar) calendar.get();
        cal.set(Calendar.YEAR, (int) ((dosTime >> 25) & 0xff) + 1980);
        cal.set(Calendar.MONTH, (int) ((dosTime >> 21) & 0x0f) - 1);
        cal.set(Calendar.DATE, (int) (dosTime >> 16) & 0x1f);
        cal.set(Calendar.HOUR_OF_DAY, (int) (dosTime >> 11) & 0x1f);
        cal.set(Calendar.MINUTE, (int) (dosTime >> 5) & 0x3f);
        cal.set(Calendar.SECOND, (int) (dosTime << 1) & 0x3e);
        // According to the ZIP file format specification, its internal time
        // has only two seconds granularity.
        // Make calendar return only total seconds in order to make
        // getTime work correctly.
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Converts unix time to a DOS date/time field.
     *
     * @param time The number of milliseconds from the epoch.
     *
     * @return The DOS date/time field.
     */
    protected static long java2dosTime(long time) {
        final Calendar cal = (Calendar) calendar.get();
        cal.setTimeInMillis(time);
        int year = cal.get(Calendar.YEAR);
        if (year < 1980)
            return MIN_DOS_TIME;
        return (((year - 1980) & 0xff) << 25)
             | ((cal.get(Calendar.MONTH) + 1) << 21)
             | (cal.get(Calendar.DAY_OF_MONTH) << 16)
             | (cal.get(Calendar.HOUR_OF_DAY) << 11)
             | (cal.get(Calendar.MINUTE) << 5)
             | (cal.get(Calendar.SECOND) >> 1);
    }

    private static final ThreadLocal calendar = new ThreadLocal() {
        protected Object initialValue() {
            return new GregorianCalendar();
        }
    };
}