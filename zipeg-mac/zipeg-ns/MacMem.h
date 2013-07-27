#ifndef __MacMem_h__
#define __MacMem_h__

#ifdef __cplusplus
extern "C" {
#endif
    
typedef struct {
    uint64_t ram;    // -1 or physical memory size in bytes (valid after macmem_hook_malloc() call)
    uint64_t bytes;  // total number of bytes allocated
    uint64_t allocs; // number of individual allocated chunks
} MemoryStatistics;

extern MemoryStatistics mstat;

void macmem_hook_malloc(int bytesInSafetyPool);
void macmem_free_safety_pool();
void macmem_unhook_malloc();

#ifdef __cplusplus
}
#endif

#endif
