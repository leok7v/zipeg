/*
 * ZipConstants.java
 *
 * Created on 22. Juli 2005, 23:08
 */
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

/**
 * Some constants used for ZIP compatible files.
 *
 * @author Christian Schlichtherle
 */
interface ZipConstants {

    /**
     * Local File Header signature.
     */
    int LFH_SIG = 0x04034B50;
    
    /**
     * Data Descriptor signature.
     */
    int DD_SIG = 0x08074B50;
    
    /**
     * Central File Header signature.
     */
    int CFH_SIG = 0x02014B50;

    /**
     * End Of Central Directory signature.
     */
    int EOCD_SIG = 0x06054B50;

    /**
     * The minimum length of the Local File Header record.
     */
    int LFH_MIN_LEN =
        /* local file header signature     */ 4 +
        /* version needed to extract       */ 2 +
        /* general purpose bit flag        */ 2 +
        /* compression method              */ 2 +
        /* last mod file time              */ 2 +
        /* last mod file date              */ 2 +
        /* crc-32                          */ 4 +
        /* compressed size                 */ 4 +
        /* uncompressed size               */ 4 +
        /* file name length                */ 2 +
        /* extra field length              */ 2;

    /**
     * The minimum length of the Central File Header record.
     */
    int CFH_MIN_LEN =
        /* central file header signature   */ 4 +
        /* version made by                 */ 2 +
        /* version needed to extract       */ 2 +
        /* general purpose bit flag        */ 2 +
        /* compression method              */ 2 +
        /* last mod file time              */ 2 +
        /* last mod file date              */ 2 +
        /* crc-32                          */ 4 +
        /* compressed size                 */ 4 +
        /* uncompressed size               */ 4 +
        /* filename length                 */ 2 +
        /* extra field length              */ 2 +
        /* file comment length             */ 2 +
        /* disk number start               */ 2 +
        /* internal file attributes        */ 2 +
        /* external file attributes        */ 4 +
        /* relative offset of local header */ 4;

    /**
     * The minimum length of the End Of Central Directory record.
     */
    int EOCD_MIN_LEN =
        /* end of central dir signature    */ 4 +
        /* number of this disk             */ 2 +
        /* number of the disk with the     */   +
        /* start of the central directory  */ 2 +
        /* total number of entries in      */   +
        /* the central dir on this disk    */ 2 +
        /* total number of entries in      */   +
        /* the central dir                 */ 2 +
        /* size of the central directory   */ 4 +
        /* offset of start of central      */   +
        /* directory with respect to       */   +
        /* the starting disk number        */ 4 +
        /* zipfile comment length          */ 2;

    /**
     * Compression method for uncompressed (stored) entries.
     */
    short STORED = 0;

    /**
     * Compression method for compressed (deflated) entries.
     */
    short DEFLATED = 8;

    short PLATFORM_FAT  = 0;
    short PLATFORM_UNIX = 3;

    /**
     * Smallest date/time ZIP can handle.
     */
    long MIN_DOS_TIME = 0x210000l;
    
    /**
     * The default character set used for entry names and comments in a ZIP
     * compatible file.
     * This is <tt>JAR_ENCODING</tt> for compatibility with Sun's JDK
     * implementation.
     * Note that you should use <tt>ZIP_ENCODING</tt> for ordinary ZIP files
     * instead.
     */
    String DEFAULT_ENCODING = "UTF-8";
    
    /**
     * The buffer size used for deflating and inflating.
     * Optimized for reading and writing flash memory media.
     */
    int FLATER_BUF_LENGTH = 64 * 1024;
}