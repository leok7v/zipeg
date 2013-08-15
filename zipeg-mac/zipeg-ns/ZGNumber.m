#import "ZGNumber.h"

@implementation ZGNumber {
@private
    ZGNumberKind _kind;
    uint64_t _storage;
}

// TODO: static objects for _TRUE _FALSE 0 -1 and 1 may save some memory and speed up things a bit
// it worth measuring it first

- (ZGNumberKind)kind { return _kind; }
- (char)charValue { return (char)_storage; }
- (short)shortValue { return (short)_storage; }
- (int)intValue { return (int)_storage; }
- (long long)longLongValue { return (long long)_storage; }

- (unsigned char)unsignedCharValue { return (char)_storage; }
- (unsigned short)unsignedShortValue { return (short)_storage; }
- (unsigned int)unsignedIntValue { return (int)_storage; }
- (unsigned long long)unsignedLongLongValue { return (long long)_storage; }

- (BOOL)boolValue { return (BOOL)_storage; }
- (float)floatValue { return *(float*)&_storage; }
- (double)doubleValue { return *(double*)&_storage; }

- (ZGNumber *)initWithChar:(char)v { _storage = v; _kind = kI1; return self; }
- (ZGNumber *)initWithShort:(short)v { _storage = v; _kind = kI2; return self; }
- (ZGNumber *)initWithInt:(int)v { _storage = v; _kind = kI4; return self; }
- (ZGNumber *)initWithLongLong:(long long)v { _storage = v; _kind = kI8; return self; }

- (ZGNumber *)initWithUnsignedChar:(unsigned char)v  { _storage = v; _kind = kUI1; return self; }
- (ZGNumber *)initWithUnsignedShort:(unsigned short)v  { _storage = v; _kind = kUI2; return self; }
- (ZGNumber *)initWithUnsignedInt:(unsigned int)v  { _storage = v; _kind = kUI4; return self; }
- (ZGNumber *)initWithUnsignedLongLong:(unsigned long long)v  { _storage = v; _kind = kUI8; return self; }

- (ZGNumber *)initWithBool:(BOOL)v { _storage = v; _kind = kB; return self; }
- (ZGNumber *)initWithFloat:(float)v { _storage = *(unsigned int*)&v; _kind = kF4; return self; }
- (ZGNumber *)initWithDouble:(double)v { _storage = *(unsigned long long*)&v; _kind = kF8; return self; }

+ (ZGNumber *)numberWithChar:(char)v { return [[ZGNumber alloc] initWithChar:v]; }
+ (ZGNumber *)numberWithShort:(short)v { return [[ZGNumber alloc] initWithShort:v]; }
+ (ZGNumber *)numberWithInt:(int)v { return [[ZGNumber alloc] initWithInt:v]; }
+ (ZGNumber *)numberWithLongLong:(long long)v { return [[ZGNumber alloc] initWithLongLong:v]; }

+ (ZGNumber *)numberWithUnsignedChar:(unsigned char)v { return [[ZGNumber alloc] initWithUnsignedChar:v]; }
+ (ZGNumber *)numberWithUnsignedShort:(unsigned short)v { return [[ZGNumber alloc] initWithUnsignedShort:v]; }
+ (ZGNumber *)numberWithUnsignedInt:(unsigned int)v { return [[ZGNumber alloc] initWithUnsignedInt:v]; }
+ (ZGNumber *)numberWithUnsignedLongLong:(unsigned long long)v { return [[ZGNumber alloc] initWithUnsignedLongLong:v]; }

+ (ZGNumber *)numberWithBool:(BOOL)v { return [[ZGNumber alloc] initWithBool:v]; }
+ (ZGNumber *)numberWithFloat:(float)v { return [[ZGNumber alloc] initWithFloat:v]; }
+ (ZGNumber *)numberWithDouble:(double)v { return [[ZGNumber alloc] initWithDouble:v]; }

- (NSString *)stringValue {
    if (self == null) {
        return @"nil";
    } else if (kI1 <= _kind && _kind <= kI8) {
        return [NSString stringWithFormat:@"%lld", (long long)_storage];
    } else if (kUI1 <= _kind && _kind <= kUI8) {
        return [NSString stringWithFormat:@"%llu", _storage];
    } else if (_kind == kF4) {
        return [NSString stringWithFormat:@"%f", (double)*(float*)&_storage];
    } else if (_kind == kF8) {
        return [NSString stringWithFormat:@"%f", *(double*)&_storage];
    } else if (_kind == kB) {
        return _storage ? @"true" : @"false";
    } else {
        assert(false);
        return @"???";
    }
}

- (NSComparisonResult)compare:(ZGNumber *)number {
    if (kUI1 <= _kind && _kind <= kUI8) {
        assert(kUI1 <= number.kind && number.kind <= kUI8);
        unsigned long long sv = self.unsignedLongLongValue;
        unsigned long long nv = number.unsignedLongLongValue;
        return sv == nv ? NSOrderedSame : (sv < nv ? NSOrderedAscending : NSOrderedDescending);
    } else if ((kI1 <= _kind && _kind <= kI8) || _kind == kB) {
        assert((kI1 <= number.kind && number.kind <= kI8) || _kind == kB);
        long long sv = self.unsignedLongLongValue;
        long long nv = number.unsignedLongLongValue;
        return sv == nv ? NSOrderedSame : (sv < nv ? NSOrderedAscending : NSOrderedDescending);
    } else if (_kind == kF4) {
        assert(number.kind <= kF4);
        float sv = self.floatValue;
        float nv = number.floatValue;
        return sv == nv ? NSOrderedSame : (sv < nv ? NSOrderedAscending : NSOrderedDescending);
    } else if (_kind == kF8) {
        assert(number.kind <= kF8);
        double sv = self.doubleValue;
        double nv = number.doubleValue;
        return sv == nv ? NSOrderedSame : (sv < nv ? NSOrderedAscending : NSOrderedDescending);
    } else {
        assert(false);
    }
}

- (BOOL)isEqualToNumber:(ZGNumber *)number {
    return self.kind == number.kind && [self compare:number] == 0;
}

- (NSString *)description {
    switch (self.kind) {
        case kI1 : return [NSString stringWithFormat: @"[ZGNumber int8 0x%02llx %lld str=%@]", _storage, _storage, [self stringValue]];
        case kI2 : return [NSString stringWithFormat: @"[ZGNumber int16 0x%04llx %lld str=%@]", _storage, _storage, [self stringValue]];
        case kI4 : return [NSString stringWithFormat: @"[ZGNumber int32 0x%08llx %lld str=%@]", _storage, _storage, [self stringValue]];
        case kI8 : return [NSString stringWithFormat: @"[ZGNumber int64 0x%016llx %lld str=%@]", _storage, _storage, [self stringValue]];
        case kUI1: return [NSString stringWithFormat: @"[ZGNumber uint8 0x%02llx %llu str=%@]", _storage, _storage, [self stringValue]];
        case kUI2: return [NSString stringWithFormat: @"[ZGNumber uint16 0x%04llx %llu str=%@]", _storage, _storage, [self stringValue]];
        case kUI4: return [NSString stringWithFormat: @"[ZGNumber uint32 0x%08llx %llu str=%@]", _storage, _storage, [self stringValue]];
        case kUI8: return [NSString stringWithFormat: @"[ZGNumber uint64 0x%016llx %llu str=%@]", _storage, _storage, [self stringValue]];
        case kF4 : return [NSString stringWithFormat: @"[ZGNumber float 0x%08llx %f str=%@]", _storage, (double)*(float*)&_storage, [self stringValue]];
        case kF8 : return [NSString stringWithFormat: @"[ZGNumber double 0x%016llx %f str=%@]", _storage, *(double*)&_storage, [self stringValue]];
        case kB  : return [NSString stringWithFormat: @"[ZGNumber bool 0x%02llx %@]", _storage, [self stringValue]];
        default:
            assert(false);
            return [NSString stringWithFormat: @"malformed ZGNumber kind=%d 0x%016llx %lld", self.kind, _storage, _storage];
    }
}

@end
