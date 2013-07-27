#ifdef __cplusplus
extern "C" {
#endif

FOUNDATION_EXPORT NSString *const ZGAppErrorDomain;

void ZGErrorsInit();

enum {
    ZGInternalError = 1000,
    ZGIsNotAFile = 1001,
    ZGFileIsNotReachable = 1002,
    ZGArchiverError = 1003,
    ZGOutOfMemory = 1999
};

NSError* ZGOutOfMemoryError();

#define ZG_ERROR_KEY(code)                    [NSString stringWithFormat:@"%d", code]
#define ZG_ERROR_LOCALIZED_DESCRIPTION(code)  NSLocalizedStringFromTable(ZG_ERROR_KEY(code), @"ZGErrors", nil)

#ifdef __cplusplus
}
#endif

