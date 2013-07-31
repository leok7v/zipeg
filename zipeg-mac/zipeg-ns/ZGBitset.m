#import "ZGBitset.h"

#define N ((int)sizeof(UInt64)*8)

@interface ZGBitset() {
    UInt64 *_bits;
    int _numBits;
}
@end


@implementation ZGBitset

- (void)dealloc {
    free(_bits);
    dealloc_count(self);
    trace(@"");
}

- (id)initWithCapacity:(int)numBits {
    if (self) {
        alloc_count(self);
        _numBits = numBits;
        int n = (numBits + N - 1) / N;
        _bits = (UInt64 *)calloc(n, sizeof(UInt64));
    }
    return _bits ? self : null;
}

- (void)setBit:(int)index to:(bool)value {
    if (index < 0 || index >= _numBits) {
        @throw NSRangeException;
    }
    int ix = index / N;
    UInt64 mask = (1ULL << (index % N));
    if (value) {
        _bits[ix] |= mask;
    } else {
        _bits[ix] &= ~mask;
    }
}

- (BOOL)isSet:(int)index {
    if (0 <= index && index < _numBits) {
        int ix = index / N;
        UInt64 mask = (1ULL << (index % N));
        return (_bits[ix] & mask) != 0;
    } else {
        return false;
    }
}

- (void)clear {
    int n = (_numBits + N - 1) / N;
    memset(_bits, 0, n * sizeof(UInt64));
}

- (void)fill {
    int n = (_numBits + N - 1) / N;
    memset(_bits, 0xFFU, n * sizeof(UInt64));
}

+ (id)bitsetWithCapacity:(int)numBits {
    return [[ZGBitset alloc] initWithCapacity:numBits];
}

@end
