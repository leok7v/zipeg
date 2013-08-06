#ifndef __MacMem_h__
#define __MacMem_h__

#ifdef __cplusplus
extern "C" {
#endif
    
typedef struct {
    int64_t ram;    // -1 or physical memory size in bytes (valid after macmem_hook_malloc() call)
    int64_t bytes;  // total number of bytes allocated
    int64_t allocs; // number of individual allocated chunks
} MemoryStatistics;

extern MemoryStatistics mstat;

#ifdef DEBUG

void macmem_hook_malloc();
void macmem_unhook_malloc();

#endif
    
#ifdef __cplusplus
}
#endif

#endif
