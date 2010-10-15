/**
 * @file
 * @brief Various macros.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_MACRO_HPP_
#define VMDKBACKUP_MACRO_HPP_

#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include <time.h>
#include <sys/time.h>

/**
 * Defnition of constant values and macros.
 */
#ifdef DEBUG
#define VERBOSE 1
#else
#define VERBOSE 0
#endif /* DEBUG */

#define PRINT_NOW()                                                   \
    {                                                                 \
        struct timeval tv;                                            \
        ::gettimeofday(&tv, 0);                                       \
        struct tm t;                                                  \
        ::localtime_r(&tv.tv_sec, &t);                                \
        ::printf("%d-%02d-%02d %02d:%02d:%02d.%03d",                  \
                 t.tm_year + 1900, t.tm_mon, t.tm_mday,               \
                 t.tm_hour, t.tm_min, t.tm_sec,                       \
                 (int)tv.tv_usec / 1000);                             \
    }                                                                 

#define ASSERT(x)                                                     \
    if (!x) {                                                         \
        ::printf("%s:%d error " #x "\n", __FILE__, __LINE__);         \
        ::exit(1);                                                    \
    }

#define ASSERT_EQUAL(x, y)                                            \
    if (x != y) {                                                     \
        ::printf("%s:%d error not equal " #x                          \
                 ", " #y "\n", __FILE__, __LINE__);                   \
        ::exit(1);                                                    \
    }

#define WRITE_LOG(level, ...)                                         \
    if (VERBOSE >= level) {                                           \
        ::printf("LOG%d[", level);                                    \
        PRINT_NOW();                                                  \
        ::printf("](%s:%d):",  __FILE__, __LINE__);                   \
        ::printf(__VA_ARGS__);                                        \
        ::fflush(stdout);                                             \
    }

#define WRITE_LOG0(...) WRITE_LOG(0, __VA_ARGS__)
#define WRITE_LOG1(...) WRITE_LOG(1, __VA_ARGS__)

#endif /* VMDKBACKUP_MACRO_HPP_ */
