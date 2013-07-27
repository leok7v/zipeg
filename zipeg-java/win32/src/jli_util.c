/*
 * @(#)jli_util.c	1.2	06/02/25
 *
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

#include <stdio.h>
#include <string.h>
#include "jli_util.h"

#ifdef _WIN32
#pragma warning(disable :4996) // This function or variable may be unsafe
#endif

/*
 * Returns a pointer to a block of at least 'size' bytes of memory.
 * Prints error message and exits if the memory could not be allocated.
 */
void *
JLI_MemAlloc(size_t size)
{
    void *p = malloc(size);
    if (p == 0) {
	perror("malloc");
	exit(1);
    }
    return p;
}

/*
 * Equivalent to realloc(size).
 * Prints error message and exits if the memory could not be reallocated.
 */
void *
JLI_MemRealloc(void *ptr, size_t size)
{
    void *p = realloc(ptr, size);
    if (p == 0) {
	perror("realloc");
	exit(1);
    }
    return p;
}

/*
 * Wrapper over strdup(3C) which prints an error message and exits if memory
 * could not be allocated.
 */
char *
JLI_StringDup(const char *s1)
{
    char *s = strdup(s1);
    if (s == NULL) {
        perror("strdup");
        exit(1);
    }
    return s;
}

/*
 * Very equivalent to free(ptr).
 * Here to maintain pairing with the above routines.
 */
void
JLI_MemFree(void *ptr)
{
    free(ptr);
}
