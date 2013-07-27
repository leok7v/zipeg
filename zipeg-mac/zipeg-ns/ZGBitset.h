@interface ZGBitset : NSObject

+ (id)bitsetWithCapacity:(int)numBits;
- (void)setBit:(int)index to:(bool)value;
- (BOOL)isSet:(int)index;
- (void)clear; // set all bits to zero
- (void)fill;  // set all bits to one

@end
