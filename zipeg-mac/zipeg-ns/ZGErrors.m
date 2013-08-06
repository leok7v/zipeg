#import "ZGErrors.h"
#import "MacMem.h"

NSString *const ZGAppErrorDomain = @"com.zipeg.errors";
NSError* ZGOOM;

NSError* ZGOutOfMemoryError() {
    return ZGOOM;
}

void ZGErrorsInit() {
    NSMutableDictionary *details = [NSMutableDictionary dictionary];
    [details setValue:ZG_ERROR_LOCALIZED_DESCRIPTION(ZGOutOfMemory) forKey:NSLocalizedDescriptionKey];
    ZGOOM = [NSError errorWithDomain:ZGAppErrorDomain code:ZGIsNotAFile userInfo:details];
}
