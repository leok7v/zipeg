#define kIconImageSize 16.0

@interface ZGImages : NSObject

+ (ZGImages*) shared;
@property (readonly, nonatomic) NSImage* docImage;
@property (readonly, nonatomic) NSImage* dirImage;
@property (readonly, nonatomic) NSImage* dirOpen;
@property (readonly, nonatomic) NSImage* appImage;

+ (NSImage*) iconForFileType16x16: (NSString*) pathExtension;
+ (NSImage*) iconForFileType: (NSString*) pathExtension;
+ (NSImage*) thumbnail: (NSString*) path;

@end
