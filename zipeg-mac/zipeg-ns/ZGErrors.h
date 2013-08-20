#ifdef __cplusplus
extern "C" {
#endif

FOUNDATION_EXPORT NSString *const ZGAppErrorDomain;

void ZGErrorsInit();

enum {
    kInternalError = 1000,
    kIsNotAFile = 1001,
    kIsNotAFolder = 1002,
    kFileIsNotReachable = 1003,
    kArchiverError = 1004,
    kOutOfMemory = 1999
};

FOUNDATION_EXPORT NSError* ZGOutOfMemoryError();
FOUNDATION_EXPORT NSError* ZGInternalError();

#define ZG_ERROR_KEY(code)                    [NSString stringWithFormat:@"%d", code]
#define ZG_ERROR_LOCALIZED_DESCRIPTION(code)  NSLocalizedStringFromTable(ZG_ERROR_KEY(code), @"ZGErrors", nil)

#ifdef __cplusplus
}
#endif
