#import "ZGErrors.h"
#import "MacMem.h"

NSString *const ZGAppErrorDomain = @"com.zipeg.errors";
NSError* OOM;
NSError* InternalError;

NSError* ZGOutOfMemoryError() {
    return OOM;
}

NSError* ZGInternalError() {
    return InternalError;
}

void ZGErrorsInit() {
    OOM = [NSError errorWithDomain: ZGAppErrorDomain code: kOutOfMemory userInfo:
             @{ NSLocalizedDescriptionKey: ZG_ERROR_LOCALIZED_DESCRIPTION(kOutOfMemory) }];
    InternalError = [NSError errorWithDomain: ZGAppErrorDomain code: kInternalError userInfo:
             @{ NSLocalizedDescriptionKey: ZG_ERROR_LOCALIZED_DESCRIPTION(kInternalError) }];
}
