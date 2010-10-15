/**
 * @file
 * @brief Utility data and functions.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_UTIL_HPP_
#define VMDKBACKUP_UTIL_HPP_

#include <iostream>
#include <string>
#include <sstream>
#include <vector>

#include <stdio.h>
#include <string.h>
#include <time.h>
#include <sys/time.h>
#include <assert.h>

#include "vixDiskLib.h"

#include "macro.hpp"
#include "version.hpp"

const int DEFAULT_BLOCK_SIZE = 1048576; /* 1MB */

/* Default config path for VDDK */
#define DEFAULT_CONFIG_PATH "/usr/local/lib/vmware-vix-disklib/config"
/* Default libdir for VDDK */
#define DEFAULT_LIBDIR "/usr/local/lib/vmware-vix-disklib"
/* Default lock file to initialize/finalize vddk and open/close vmdk. */
#define DEFAULT_LOCK_FILE_NAME "/var/tmp/vmdkbkp.lock"
/* Default lock resource name. */
#define DEFAULT_LOCK_RESOURCE_NAME "vmdkbkp_lock"

/**
 * @brief Backup command.
 */
enum BackupCommand {
    CMD_DUMP,
    CMD_DUMPTEST,
    CMD_RESTORE,
    CMD_CHECK,
    CMD_PRINT,
    CMD_DIGEST,
    CMD_MERGE,
    CMD_RDIFF,
    CMD_LOCK,
    CMD_HELP,
    CMD_UNKNOWN
};

/**
 * @brief Dump mode.
 */
enum DumpMode {
    DUMPMODE_FULL,
    DUMPMODE_DIFF,
    DUMPMODE_INCR,
    DUMPMODE_UNKNOWN
};

/**
 * @brief Whole configuration data for the software.
 */
struct ConfigData {

    std::string programName;
    std::string versionStr;
    
    /* Command string */
    std::string cmdStr;

    /* Command as a result of parsing cmdStr. */
    BackupCommand cmd;

    /* Command mode.
       Currently supported for dump command only. */
    DumpMode mode;

    /* True when dealing with remote (managed, not hosted) vmdk file. */
    bool isRemote;

    /* Parameters to connect vcenter for remote vmdk file */
    char *server; 
    char *username;
    char *password;
    char *vmMorefStr;
    char *vmdkPath;
    char *snapshotStr;

    /* True when creating new vmdk file. */
    bool isCreate; 

    /* blocksize * nSectorsPerBlock --> # of sectors in the vmdk. */
    const uint32 sectorSize;
    size_t blocksize;
    uint32 nSectorsPerBlock;

    /* Required by VDDK library for SAN transfer. */
    const char *configPath;
    const char *libDir;

    /* True if write zero-block also. */
    bool isWriteZeroBlock;

    /* True if backup/restore via SAN. */
    bool isUseSan;

    /* True if write metadata in restore. */
    bool isWriteMetadata;

    /* Filename of archives. */
    std::string dumpInFileName;
    std::string digestInFileName;
    std::string dumpOutFileName;
    std::string digestOutFileName;
    std::string bmpInFileName;
    std::string rdiffOutFileName;

    /* Filenames of dump/rdiff for merge command. */
    std::vector<std::string> archiveList;

    /* Lock file name */
    std::string lockFileName; /* obsolute */
    /* Lock resource name */
    std::string lockResourceName;

    /* Use shared lock */
    bool useSharedLock;

    /* Number of read blocks for dumptest command.
       0 means whole disk. */
    size_t numReadBlockForTest;
    
    /**
     * Constructor
     */
    ConfigData()
        : versionStr(VMDKBKP_VERSION)
        , cmd(CMD_UNKNOWN)
        , mode(DUMPMODE_UNKNOWN)
        , isRemote(false)
        , server(0)
        , username(0)
        , password(0)
        , vmMorefStr(0)
        , vmdkPath(0)
        , snapshotStr(0)
        , isCreate(false)
        , sectorSize(VIXDISKLIB_SECTOR_SIZE)
        , blocksize(DEFAULT_BLOCK_SIZE)
        , nSectorsPerBlock(blocksize / sectorSize)
        , configPath(DEFAULT_CONFIG_PATH)
        , libDir(DEFAULT_LIBDIR)
        , isWriteZeroBlock(true)
        , isUseSan(false)
        , isWriteMetadata(false)
        , dumpInFileName()
        , digestInFileName()
        , dumpOutFileName()
        , digestOutFileName()
        , bmpInFileName()
        , rdiffOutFileName()
        , archiveList(0)
        , lockFileName(DEFAULT_LOCK_FILE_NAME)
        , lockResourceName(DEFAULT_LOCK_RESOURCE_NAME)
        , useSharedLock(false)
        , numReadBlockForTest(0)
        {}
    /**
     * Set cmd;
     */
    void setCmd(const std::string& cmdStr) {

        if (cmdStr == "dump") {
            cmd = CMD_DUMP;
        } else if (cmdStr == "dumptest") {
            cmd = CMD_DUMPTEST;
        } else if (cmdStr == "restore") {
            cmd = CMD_RESTORE;
        } else if (cmdStr == "check") {
            cmd = CMD_CHECK;
        } else if (cmdStr == "print") {
            cmd = CMD_PRINT;
        } else if (cmdStr == "digest") {
            cmd = CMD_DIGEST;
        } else if (cmdStr == "merge") {
            cmd = CMD_MERGE;
        } else if (cmdStr == "rdiff") {
            cmd = CMD_RDIFF;
        } else if (cmdStr == "lock") {
            cmd = CMD_LOCK;
        } else if (cmdStr == "help") {
            cmd = CMD_HELP;
        } else {
            cmd = CMD_UNKNOWN;
        }
    }
    /**
     * Set mode;
     */
    void setMode(const std::string& modeStr) {

        if (modeStr == "full") {
            mode = DUMPMODE_FULL;
        } else if (modeStr == "diff") {
            mode = DUMPMODE_DIFF;
        } else if (modeStr == "incr") {
            mode = DUMPMODE_INCR;
        } else {
            mode = DUMPMODE_UNKNOWN;
        }
    }
};

/**
 * @brief Information of vmdk file.
 *
 * Filled with the result of VixDiskLib_GetInfo().
 */
struct VmdkInfo {

    /* Assigned using readVmdkInfo(). */
    VixDiskLibAdapterType adapterType;
    uint64 nBlocks;
    int32 numLinks;

    /**
     * vmdk format version.
     * 7 for ESX(i)4.
     *
     * This is stored not in VixDiskLibInfo structure, but in metadata.
     * Now we deal with this value fixed 7 when creation or restore.
     */
    /* uint32 hwVersion; */

    /**
     * toString().
     */
    std::string toString() {
        std::stringstream ss;
        ss << "adapterType: " << adapterType
           << " nBlocks: " << nBlocks
           << " numLinks: " << numLinks;
        return ss.str();
    }
    
    /**
     * Stream operators.
     */
    friend std::ostream& operator<<(std::ostream& os, const VmdkInfo& vmdkInfo);
    friend std::istream& operator>>(std::istream& is, VmdkInfo& vmdkInfo);
};

/**
 * Equality check of struct tm.
 */
inline bool operator==(const tm& l, const tm& r)
{
    return (l.tm_sec == r.tm_sec &&
            l.tm_min == r.tm_min &&
            l.tm_hour == r.tm_hour &&
            l.tm_mday == r.tm_mday &&
            l.tm_mon == r.tm_mon &&
            l.tm_year == r.tm_year &&
            l.tm_wday == r.tm_wday &&
            l.tm_yday == r.tm_yday &&
            l.tm_isdst == r.tm_isdst);
}

/**
 * @brief A wrapper of struct tm
 *
 * The value of sizeof(time_t) is 8 in 64bit linux.
 * It's enough to our purpose.
 */
class TimeStamp {
private:
    struct tm timeM_;
    
    int64 compareTo(const TimeStamp& rhs) const {
        uint64 left = static_cast<uint64>(getTimeStamp());
        uint64 right = static_cast<uint64>(rhs.getTimeStamp());
        int64 leftI = static_cast<int64>(left);
        int64 rightI = static_cast<int64>(right);
        assert(leftI >= 0);
        assert(rightI >= 0);
        return leftI - rightI;
    }

public:
    /* Comparators */
    bool operator==(const TimeStamp& rhs) const
        {return compareTo(rhs) == 0;}
    bool operator!=(const TimeStamp& rhs) const
        {return !(*this == rhs);}
    bool operator>(const TimeStamp& rhs) const
        {return compareTo(rhs) > 0;}
    bool operator>=(const TimeStamp& rhs) const
        {return compareTo(rhs) >= 0;}
    bool operator<(const TimeStamp& rhs) const
        {return compareTo(rhs) < 0;}
    bool operator<=(const TimeStamp& rhs) const
        {return compareTo(rhs) <= 0;}

    /**
     * Set the specified time.
     */
    void setTimeStamp(const time_t time) {
        ::localtime_r(&time, &timeM_);
    }

    /**
     * Set current time.
     */
    void setTimeStamp() {
        time_t time = ::time(0);
        setTimeStamp(time);
    }

    time_t getTimeStamp() const {
        struct tm timeM = timeM_;
        return ::mktime(&timeM);
    }

    std::string getTimeStampStr() const {
        std::string timeStr = asctime(&timeM_);
        return timeStr;
    }

    friend std::ostream& operator<<(std::ostream& os, const TimeStamp& ts);
    friend std::istream& operator>>(std::istream& is, TimeStamp& ts);
};

/**
 * A wrapper of gettimeofday().
 */
inline double getTime()
{
    struct timeval tv;
    double timeDouble;
    
    ::gettimeofday(&tv, NULL);
        
    timeDouble = static_cast<double>(tv.tv_sec) +
        static_cast<double>(tv.tv_usec) / 1000000.0;
    return timeDouble;
}

/**
 * @return True when the filename ends with ".gz".
 */
inline bool isGzipFileName(const std::string& fileName)
{
    size_t len = fileName.length();
    return (len > 3 && fileName.substr(len - 3) == ".gz");
}

/**
 * Log func for VixDiskLib_InitEx();
 *
 * @param fmt the first argument of vprintf.
 * @param args the second argument of vprintf.
 */
void logFunc(const char *fmt, va_list args);


/******************************************************************************
 * The following macros and classes are
 * copied from vixDiskLibSample.cpp and a bit modified.
 * Rename VixDiskLibErrWrapper -> VixException
 ******************************************************************************/

#define THROW_ERROR(vixError)                                        \
   throw VixException((vixError), __FILE__, __LINE__)

#define CHECK_AND_THROW(vixError)                                    \
   do {                                                              \
      if (VIX_FAILED((vixError))) {                                  \
         throw VixException((vixError), __FILE__, __LINE__);         \
      }                                                              \
   } while (0)

/**
 * @brief Exception to handle error of VixDiskLib_*() functions from VDDK.
 */
class VixException
{
private:
    VixError errCode_;
    std::string desc_;
    std::string file_;
    int line_;

public:
    explicit VixException(VixError errCode, const char* file, int line)
        : errCode_(errCode)
        , file_(file)
        , line_(line) {
        
        char* msg = VixDiskLib_GetErrorText(errCode, NULL);
        desc_ = msg;
        VixDiskLib_FreeErrorText(msg);
    }

    VixException(const char* description, const char* file, int line)
        : errCode_(VIX_E_FAIL)
        , desc_(description)
        , file_(file)
        , line_(line) {}

    std::string description() const { return desc_; }
    VixError errorCode() const { return errCode_; }
    std::string file() const { return file_; }
    int line() const { return line_; }


    std::string sprint(const char* msg) const {
        std::stringstream ss;
        if (msg != NULL) { ss << msg << "\n"; }
        ss << "Error: [" << file() << ":" << line() << "] " <<
            std::hex << errorCode() << " " << description() << "\n";
        return ss.str();
    }

    std::string sprint() const {
        return sprint(NULL);
    }
    
    void print(const char* msg, std::ostream& os = std::cerr) const {
        os << sprint(msg);
    }

    void print(std::ostream& os = std::cerr) const {
        print(NULL, os);
    }

    void writeLog(const char* msg) const {
        std::string ret = sprint(msg);
        WRITE_LOG0("%s", ret.c_str());
    }

    void writeLog() const {
        writeLog(NULL);
    }
};

#endif /* VMDKBACKUP_UTIL_HPP_ */
