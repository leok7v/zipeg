#ifndef __NanoTime__
#define __NanoTime__

#include "c.h"

class NanoTime {
public:
    static uint64_t time(); // in nanoseconds
    static uint64_t start() { return processStarted; }
    /* usage:
     timestamp("foo");
     foo(); // ... code to measure time
     timestamp("foo");  // will print time spent calling foo()
     // differentiate the labels for nested measurements
    */
    static uint64_t timestamp(const char* label);
private:
    static uint64_t processStarted;
};

#endif /* defined(__p7z__NanoTime__) */
