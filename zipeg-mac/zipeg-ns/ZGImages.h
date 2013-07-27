#define kIconImageSize 16.0

@interface ZGImages : NSObject

+ (ZGImages*) shared;
@property (readonly, nonatomic) NSImage* docImage;
@property (readonly, nonatomic) NSImage* dirImage;

@end
