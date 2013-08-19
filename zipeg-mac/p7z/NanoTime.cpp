#include "NanoTime.hpp"
#include "HashMapS2L.hpp"
#include <CoreServices/CoreServices.h>
#include <mach/mach.h>
#include <mach/mach_time.h>

uint64_t NanoTime::time() {
    uint64_t start;
    static mach_timebase_info_data_t    sTimebaseInfo;
    start = mach_absolute_time();
    if (sTimebaseInfo.denom == 0 ) {
        (void) mach_timebase_info(&sTimebaseInfo);
    }
    start = start * sTimebaseInfo.numer / sTimebaseInfo.denom;
    return start;
}

uint64_t NanoTime::processStarted = NanoTime::time();
uint64_t delta;

static const char* SILENT = "this is silent label for delta measurement";

static HashMapS2L map(100);

static void print(const char* label, uint64_t delta) {
    if (delta < 10LL * 1000) {
        fprintf(stderr, "time: %s %lld nanoseconds\n", label, delta);
    } else if (delta < 10LL * 1000 * 1000) {
        fprintf(stderr, "time: %s %lld microseconds\n", label, delta / 1000LL);
    } else if (delta < 10LL * 1000 * 1000 * 1000) {
        fprintf(stderr, "time: %s %lld milliseconds\n", label, delta / (1000LL * 1000));
    } else {
        fprintf(stderr, "time: %s %lld seconds\n", label, delta / (1000LL * 1000 * 1000));
    }
}

uint64_t NanoTime::timestamp(const char* label) {
    if (delta == 0) {
        delta = 1;
        timestamp(SILENT);
        delta = timestamp(SILENT) + 1;
    }
    /* returns 0 on first use and delta in nanoseconds on second call */
    uint64_t t = NanoTime::time();
    uint64_t s = map.remove(label);
    if (s == 0) {
        map.put(label, t);
        return 0;
    } else {
        uint64_t d = t > s + delta ? t - s - delta : 1;
        if (label != SILENT) {
            print(label, d);
        }
        return d;
    }
}
