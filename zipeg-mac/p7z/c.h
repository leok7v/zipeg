#ifndef __c_h__
#define __c_h__

#include <_types.h>
#include <stdint.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <assert.h>
#include <wchar.h>
#ifdef __OBJC__
#import <Cocoa/Cocoa.h>
#import <Foundation/NSDebug.h>
#endif

#define null NULL
#define countof(a) (sizeof(a)/sizeof(*(a)))

#ifdef DEBUG
#ifdef __OBJC__
# define trace(fmt, ...) NSLog((@"%@:%d %s " fmt), \
    [[NSString stringWithFormat:@"%s", __FILE__] lastPathComponent], __LINE__, __PRETTY_FUNCTION__, ##__VA_ARGS__);
#else 
# define trace(fmt, ...) { \
    fprintf(stderr, "%s:%d %s ",  __FILE__, __LINE__, __PRETTY_FUNCTION__); \
    fprintf(stderr, fmt, ##__VA_ARGS__); }
#endif
#else
# define trace(...)
#endif

#ifdef __OBJC__
# define console(fmt, ...) NSLog((@"%@:%d %s " fmt), \
    [[NSString stringWithFormat:@"%s", __FILE__] lastPathComponent], __LINE__, __PRETTY_FUNCTION__, ##__VA_ARGS__);
#else
# define console(fmt, ...) { \
    fprintf(stderr, "%s:%d %s ", __FILE__, __LINE__, __PRETTY_FUNCTION__);  \
    fprintf(stderr, fmt, ##__VA_ARGS__); }
#endif

#ifdef DEBUG
#ifdef __OBJC__
# define alert(fmt, ...)  [[NSAlert alertWithMessageText:[NSString stringWithFormat:@"%@:%d\n%s", \
    [[NSString stringWithFormat:@"%s", __FILE__] lastPathComponent], __LINE__, __PRETTY_FUNCTION__] \
    defaultButton:@"OK" alternateButton:nil otherButton:nil \
    informativeTextWithFormat:fmt, ##__VA_ARGS__] runModal];
#else
# define alert(fmt, ...) { \
    fprintf(stderr, "%s:%d %s", __FILE__, __LINE__, __PRETTY_FUNCTION__); \
    fprintf(stderr, fmt, ##__VA_ARGS__); }
#endif
#else
# define alert(...)
#endif

#endif
