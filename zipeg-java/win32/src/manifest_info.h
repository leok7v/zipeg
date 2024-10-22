/*
 * @(#)manifest_info.h	1.14 05/12/05
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

#ifndef _MANIFEST_INFO_H
#define	_MANIFEST_INFO_H

#include <sys/types.h>

/*
 * Zip file header signatures
 */
#define	SIGSIZ 4		    /* size of all header signatures */
#define	LOCSIG 0x04034b50L	    /* "PK\003\004" */
#define	EXTSIG 0x08074b50L	    /* "PK\007\008" */
#define	CENSIG 0x02014b50L	    /* "PK\001\002" */
#define	ENDSIG 0x06054b50L	    /* "PK\005\006" */

/*
 * Header sizes including signatures
 */
#define	LOCHDR 30
#define	EXTHDR 16
#define	CENHDR 46
#define	ENDHDR 22

/*
 * Header field access macros
 */
#define	CH(b, n) (((unsigned char *)(b))[n])
#define	SH(b, n) (CH(b, n) | (CH(b, n+1) << 8))
#define	LG(b, n) (SH(b, n) | (SH(b, n+2) << 16))
#define	GETSIG(b) LG(b, 0)

/*
 * Macros for getting local file (LOC) header fields
 */
#define	LOCVER(b) SH(b, 4)	    /* version needed to extract */
#define	LOCFLG(b) SH(b, 6)	    /* general purpose bit flags */
#define	LOCHOW(b) SH(b, 8)	    /* compression method */
#define	LOCTIM(b) LG(b, 10)	    /* modification time */
#define	LOCCRC(b) LG(b, 14)	    /* crc of uncompressed data */
#define	LOCSIZ(b) LG(b, 18)	    /* compressed data size */
#define	LOCLEN(b) LG(b, 22)	    /* uncompressed data size */
#define	LOCNAM(b) SH(b, 26)	    /* filename length */
#define	LOCEXT(b) SH(b, 28)	    /* extra field length */

/*
 * Macros for getting extra local (EXT) header fields
 */
#define	EXTCRC(b) LG(b, 4)	    /* crc of uncompressed data */
#define	EXTSIZ(b) LG(b, 8)	    /* compressed size */
#define	EXTLEN(b) LG(b, 12)	    /* uncompressed size */

/*
 * Macros for getting central directory header (CEN) fields
 */
#define	CENVEM(b) SH(b, 4)	    /* version made by */
#define	CENVER(b) SH(b, 6)	    /* version needed to extract */
#define	CENFLG(b) SH(b, 8)	    /* general purpose bit flags */
#define	CENHOW(b) SH(b, 10)	    /* compression method */
#define	CENTIM(b) LG(b, 12)	    /* modification time */
#define	CENCRC(b) LG(b, 16)	    /* crc of uncompressed data */
#define	CENSIZ(b) LG(b, 20)	    /* compressed size */
#define	CENLEN(b) LG(b, 24)	    /* uncompressed size */
#define	CENNAM(b) SH(b, 28)	    /* length of filename */
#define	CENEXT(b) SH(b, 30)	    /* length of extra field */
#define	CENCOM(b) SH(b, 32)	    /* file comment length */
#define	CENDSK(b) SH(b, 34)	    /* disk number start */
#define	CENATT(b) SH(b, 36)	    /* internal file attributes */
#define	CENATX(b) LG(b, 38)	    /* external file attributes */
#define	CENOFF(b) LG(b, 42)	    /* offset of local header */

/*
 * Macros for getting end of central directory header (END) fields
 */
#define	ENDSUB(b) SH(b, 8)	    /* number of entries on this disk */
#define	ENDTOT(b) SH(b, 10)	    /* total number of entries */
#define	ENDSIZ(b) LG(b, 12)	    /* central directory size */
#define	ENDOFF(b) LG(b, 16)	    /* central directory offset */
#define	ENDCOM(b) SH(b, 20)	    /* size of zip file comment */

/*
 * A comment of maximum length of 64kb can follow the END record. This
 * is the furthest the END record can be from the end of the file.
 */
#define	END_MAXLEN	(0xFFFF + ENDHDR)

/*
 * Supported compression methods.
 */
#define	STORED	    0
#define	DEFLATED    8

/*
 * Information from the CEN entry to inflate a file.
 */
typedef struct zentry {	/* Zip file entry */
    size_t	isize;	/* size of inflated data */
    size_t	csize;	/* size of compressed data (zero if uncompressed) */
    off_t	offset;	/* position of compressed data */
    int		how;	/* compression method (if any) */
} zentry;

/*
 * Information returned from the Manifest file by the ParseManifest() routine.
 * Certainly (much) more could be returned, but this is the information
 * currently of interest to the C based Java utilities (particularly the
 * Java launcher).
 */
typedef struct manifest_info {	/* Interesting fields from the Manifest */
    char	*manifest_version;	/* Manifest-Version string */
    char	*main_class;		/* Main-Class entry */
    char	*jre_version;		/* Appropriate J2SE release spec */
    char	jre_restrict_search;	/* Restricted JRE search */
    char	*splashscreen_image_file_name; /* splashscreen image file */
} manifest_info;

/*
 * Attribute closure to provide to manifest_iterate.
 */
typedef void (*attribute_closure)(const char *name, const char *value,
	void *user_data);

/*
 * Function prototypes.
 */
int	JLI_ParseManifest(char *jarfile, manifest_info *info);
void	*JLI_JarUnpackFile(const char *jarfile, const char *filename,
		int *size);
void	JLI_FreeManifest(void);
int	JLI_ManifestIterate(const char *jarfile, attribute_closure ac,
		void *user_data);

#endif	/* _MANIFEST_INFO_H */
