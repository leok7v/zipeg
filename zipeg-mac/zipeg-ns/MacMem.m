#include "MacMem.h"
#include <stdlib.h>
#include <malloc/malloc.h>
#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <mach/vm_map.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/sysctl.h>
#include <pthread.h>

// see: http://www.cocoawithlove.com/2010/05/look-at-how-malloc-works-on-mac.html
//      http://www.opensource.apple.com/source/Libc/Libc-594.1.4/gen/magazine_malloc.c

extern malloc_zone_t** malloc_zones;
static malloc_zone_t* defaultZone;
static malloc_zone_t  saveZone;

static void*(*saved_malloc)(struct _malloc_zone_t *zone, size_t size);
static void*(*saved_calloc)(struct _malloc_zone_t *zone, size_t num_items, size_t size);
static void*(*saved_valloc)(struct _malloc_zone_t *zone, size_t size);
static void (*saved_free)(struct _malloc_zone_t *zone, void *ptr);
static void*(*saved_realloc)(struct _malloc_zone_t *zone, void *ptr, size_t size);

static unsigned (*saved_batch_malloc)(struct _malloc_zone_t *zone, size_t size, void **results, unsigned num_requested);
static void (*saved_batch_free)(struct _malloc_zone_t *zone, void **to_be_freed, unsigned num_to_be_freed);
static void *(*saved_memalign)(struct _malloc_zone_t *zone, size_t alignment, size_t size);
static void (*saved_free_definite_size)(struct _malloc_zone_t *zone, void *ptr, size_t size);

MemoryStatistics mstat;
static void* safety_pool;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

static uint64_t physmem() {
    uint64_t	hw_memsize = 0;
    size_t	uint64_t_size = sizeof(hw_memsize);
    if (0 == sysctlbyname("hw.memsize", &hw_memsize, &uint64_t_size, 0, 0)) {
        return hw_memsize;
    } else {
        return -1;
    }
}

static void* hook_malloc(struct _malloc_zone_t *zone, size_t size) {
    @try {
        pthread_mutex_lock(&mutex);
        void* ptr = saved_malloc(zone, size);
        if (ptr != null) {
            mstat.allocs++;
            mstat.bytes += malloc_size(ptr);
        }
        return ptr;
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void* hook_calloc(struct _malloc_zone_t *zone, size_t num_items, size_t size) {
    @try {
        pthread_mutex_lock(&mutex);
        void* ptr = saved_calloc(zone, num_items, size);
        if (ptr != null) {
            mstat.allocs++;
            mstat.bytes += malloc_size(ptr);
        }
        return ptr;
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void* hook_valloc(struct _malloc_zone_t *zone, size_t size) {
    @try {
        pthread_mutex_lock(&mutex);
        void* ptr = saved_valloc(zone, size);
        if (ptr != null) {
            mstat.allocs++;
            mstat.bytes += malloc_size(ptr);
        }
        return ptr;
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void hook_free(struct _malloc_zone_t *zone, void *ptr) {
    @try {
        pthread_mutex_lock(&mutex);
        if (ptr != null) {
            assert(mstat.bytes >= malloc_size(ptr)); // set env NO_MEMHOOK if this fires
            mstat.bytes -= malloc_size(ptr);
            assert(mstat.allocs > 0);
            mstat.allocs--;
        }
        saved_free(zone, ptr);
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void* hook_realloc(struct _malloc_zone_t *zone, void *ptr, size_t size) {
    @try {
        pthread_mutex_lock(&mutex);
        if (ptr != null) {
            assert(mstat.bytes >= malloc_size(ptr));
            mstat.bytes -= malloc_size(ptr);
        }
        ptr = saved_realloc(zone, ptr, size);
        if (ptr != null) {
            mstat.bytes += malloc_size(ptr);
        }
        return ptr;
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static unsigned hook_batch_malloc(struct _malloc_zone_t *zone, size_t size, void **results, unsigned num_requested) {
    @try {
        pthread_mutex_lock(&mutex);
        unsigned n = saved_batch_malloc(zone, size, results, num_requested);
        for (int i = 0; i < n; i++) {
            if (results != null && results[i] != null) {
                mstat.bytes += malloc_size(results[i]);
                mstat.allocs++;
            }
        }
        return n;
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void hook_batch_free(struct _malloc_zone_t *zone, void **to_be_freed, unsigned num_to_be_freed) {
    @try {
        pthread_mutex_lock(&mutex);
        for (int i = 0; i < num_to_be_freed; i++) {
            if (to_be_freed[i] != null) {
                assert(mstat.bytes >= malloc_size(to_be_freed[i]));
                mstat.bytes -= malloc_size(to_be_freed[i]);
                assert(mstat.allocs > 0);
                mstat.allocs--;
            }
        }
        return saved_batch_free(zone, to_be_freed, num_to_be_freed);
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void *hook_memalign(struct _malloc_zone_t *zone, size_t alignment, size_t size) {
    @try {
        pthread_mutex_lock(&mutex);
        void* ptr = saved_memalign(zone, alignment, size);
        if (ptr != null) {
            mstat.allocs++;
            mstat.bytes += malloc_size(ptr);
        }
        return ptr;
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

static void hook_free_definite_size(struct _malloc_zone_t *zone, void *ptr, size_t size) {
    @try {
        pthread_mutex_lock(&mutex);
        if (ptr != null) {
            assert(mstat.bytes >= malloc_size(ptr));
            mstat.bytes -= malloc_size(ptr);
            assert(mstat.allocs > 0);
            mstat.allocs--;
        }
        saved_free_definite_size(zone, ptr, size);
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

void macmem_free_safety_pool() {
    @try {
        pthread_mutex_lock(&mutex);
        if (safety_pool) {
            free(safety_pool);
            safety_pool = null;
        }
    } @finally {
        pthread_mutex_unlock(&mutex);
    }
}

void macmem_hook_malloc(int bytesInSafetyPool) {
    mstat.ram = physmem();
    defaultZone = malloc_default_zone();
    vm_protect(mach_task_self(), (uintptr_t)malloc_zones, 4096, 0, VM_PROT_READ|VM_PROT_WRITE);
    vm_protect(mach_task_self(), (uintptr_t)defaultZone, 4096, 0, VM_PROT_READ|VM_PROT_WRITE);
    saveZone = *defaultZone;
    saved_malloc = defaultZone->malloc;
    saved_calloc = defaultZone->calloc;
    saved_valloc = defaultZone->valloc;
    saved_free = defaultZone->free;
    saved_realloc = defaultZone->realloc;
    
    saved_batch_malloc = defaultZone->batch_malloc;
    saved_batch_free = defaultZone->batch_free;
    saved_memalign = defaultZone->memalign;
    saved_free_definite_size = defaultZone->free_definite_size;
    
    defaultZone->malloc = hook_malloc;
    defaultZone->calloc = hook_calloc;
    defaultZone->valloc = hook_valloc;
    defaultZone->free = hook_free;
    defaultZone->realloc = hook_realloc;
    
    defaultZone->batch_malloc = hook_batch_malloc;
    defaultZone->batch_free = hook_batch_free;
    defaultZone->memalign = hook_memalign;
    defaultZone->free_definite_size = hook_free_definite_size;
    if (bytesInSafetyPool != 0) {
        safety_pool = calloc(1, bytesInSafetyPool);
    }
}

void macmem_unhook_malloc() {
    defaultZone->malloc = saved_malloc;
    defaultZone->calloc = saved_calloc;
    defaultZone->valloc = saved_valloc;
    defaultZone->free = saved_free;
    defaultZone->realloc = saved_realloc;
    
    defaultZone->batch_malloc = saved_batch_malloc;
    defaultZone->batch_free = saved_batch_free;
    defaultZone->memalign = saved_memalign;
    defaultZone->free_definite_size = saved_free_definite_size;
}

