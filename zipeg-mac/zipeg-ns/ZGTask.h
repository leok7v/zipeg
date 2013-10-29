@interface ZGTask : NSObject
-(NSData*) availableDataOrError: (NSFileHandle *) file;
- (id) initWithPath:(NSString*) path;
- (void) setArgs:(NSArray*) args;
- (void) setInput:(NSData*) stdinData;
- (void) setError:(NSMutableString*) error;
- (void) setOutput:(NSMutableString*) output;
- (int) execute;
@end


