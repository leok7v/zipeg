typedef enum  {
    kI1 = 1,
    kI2 = 2,
    kI4 = 3,
    kI8 = 4,
    kF4 = 5,
    kF8 = 6,
    kB  = 20,
    kUI1 = 21,
    kUI2 = 22,
    kUI4 = 23,
    kUI8 = 24
} ZGNumberKind;

@interface ZGNumber : NSObject

@property (nonatomic, readonly) ZGNumberKind kind;

@property (nonatomic, readonly) char charValue;
@property (nonatomic, readonly) short shortValue;
@property (nonatomic, readonly) int intValue;
@property (nonatomic, readonly) long longValue;
@property (nonatomic, readonly) long long longLongValue;
@property (nonatomic, readonly) unsigned char unsignedCharValue;
@property (nonatomic, readonly) unsigned short unsignedShortValue;
@property (nonatomic, readonly) unsigned int unsignedIntValue;
@property (nonatomic, readonly) unsigned long unsignedLongValue;
@property (nonatomic, readonly) unsigned long long unsignedLongLongValue;

@property (nonatomic, readonly) BOOL boolValue;

@property (nonatomic, readonly) NSString * stringValue;

- (NSString *)description;

- (NSComparisonResult)compare:(ZGNumber *)otherNumber;

- (BOOL)isEqualToNumber:(ZGNumber *)number;

- (ZGNumber *)initWithChar:(char)value;
- (ZGNumber *)initWithShort:(short)value;
- (ZGNumber *)initWithInt:(int)value;
- (ZGNumber *)initWithLongLong:(long long)value;
- (ZGNumber *)initWithFloat:(float)value;
- (ZGNumber *)initWithDouble:(double)value;

- (ZGNumber *)initWithBool:(BOOL)value;

- (ZGNumber *)initWithUnsignedChar:(unsigned char)value;
- (ZGNumber *)initWithUnsignedShort:(unsigned short)value;
- (ZGNumber *)initWithUnsignedInt:(unsigned int)value;
- (ZGNumber *)initWithUnsignedLongLong:(unsigned long long)value;

+ (ZGNumber *)numberWithChar:(char)value;
+ (ZGNumber *)numberWithShort:(short)value;
+ (ZGNumber *)numberWithInt:(int)value;
+ (ZGNumber *)numberWithLongLong:(long long)value;

+ (ZGNumber *)numberWithBool:(BOOL)value;

+ (ZGNumber *)numberWithUnsignedChar:(unsigned char)value;
+ (ZGNumber *)numberWithUnsignedShort:(unsigned short)value;
+ (ZGNumber *)numberWithUnsignedInt:(unsigned int)value;
+ (ZGNumber *)numberWithUnsignedLongLong:(unsigned long long)value;
@end
