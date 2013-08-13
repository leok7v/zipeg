#import "ZGErrors.h"
#import "MacMem.h"

NSString *const ZGAppErrorDomain = @"com.zipeg.errors";
NSError* ZGOOM;

NSError* ZGOutOfMemoryError() {
    return ZGOOM;
}

void ZGErrorsInit() {
    ZGOOM = [NSError errorWithDomain: ZGAppErrorDomain code: ZGIsNotAFile userInfo:
             @{ NSLocalizedDescriptionKey: ZG_ERROR_LOCALIZED_DESCRIPTION(ZGOutOfMemory) }];
}
