#import <Cocoa/Cocoa.h>
#import "ZGApp.h"
#import "MacMem.h"

int main(int argc, char *argv[]) {
    BOOL hook = getenv("NO_MEMHOOK") == null;
#ifdef DEBUG
    if (hook) {
        macmem_hook_malloc(1024*1024*8);
    }
#endif
    int r = NSApplicationMain(argc, (const char **)argv);
#ifdef DEBUG
    if (hook) {
        macmem_free_safety_pool();
        macmem_unhook_malloc();
    }
#endif
    return r;
}
