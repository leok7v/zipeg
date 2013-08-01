#import <Cocoa/Cocoa.h>
#import "ZGApp.h"
#import "MacMem.h"


int main(int argc, char *argv[]) {
    macmem_hook_malloc(1024*1024*8);
    int r = NSApplicationMain(argc, (const char **)argv);
    macmem_free_safety_pool();
    macmem_unhook_malloc();
    return r;
}


