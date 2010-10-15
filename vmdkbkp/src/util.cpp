/**
 * @file
 * @brief Implementation of all defined in util.hpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include "util.hpp"
#include "serialize.hpp"

std::ostream& operator<<(std::ostream& os, const VmdkInfo& vmdkInfo)
{
    try {
        putAsString(os, static_cast<int>(vmdkInfo.adapterType));
        putAsString(os, vmdkInfo.nBlocks);
        putAsString(os, vmdkInfo.numLinks);
    } catch (ExceptionStack& e) {
        e.add("operator<<(VmdkInfo)", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, VmdkInfo& vmdkInfo)
{
    try {
        int adapterTypeInt;
        getAsString(adapterTypeInt, is);
        vmdkInfo.adapterType =
            static_cast<VixDiskLibAdapterType>(adapterTypeInt);
        getAsString(vmdkInfo.nBlocks, is);
        getAsString(vmdkInfo.numLinks, is);
    } catch (ExceptionStack& e) {
        e.add("operator>>(VmdkInfo)", __FILE__, __LINE__);
        throw;
    }
    return is;
}

std::ostream& operator<<(std::ostream& os, const TimeStamp& ts) {
    try {
        putAsString(os, ts.timeM_.tm_sec);
        putAsString(os, ts.timeM_.tm_min);
        putAsString(os, ts.timeM_.tm_hour);
        putAsString(os, ts.timeM_.tm_mday);
        putAsString(os, ts.timeM_.tm_mon);
        putAsString(os, ts.timeM_.tm_year);
        putAsString(os, ts.timeM_.tm_wday);
        putAsString(os, ts.timeM_.tm_yday);
        putAsString(os, ts.timeM_.tm_isdst);
    } catch (ExceptionStack& e) {
        e.add("operator<<(TimeStamp)", __FILE__, __LINE__);
        throw;
    }
    return os;
}

std::istream& operator>>(std::istream& is, TimeStamp& ts) {
    try {
        getAsString(ts.timeM_.tm_sec, is);
        getAsString(ts.timeM_.tm_min, is);
        getAsString(ts.timeM_.tm_hour, is);
        getAsString(ts.timeM_.tm_mday, is);
        getAsString(ts.timeM_.tm_mon, is);
        getAsString(ts.timeM_.tm_year, is);
        getAsString(ts.timeM_.tm_wday, is);
        getAsString(ts.timeM_.tm_yday, is);
        getAsString(ts.timeM_.tm_isdst, is);
    } catch (ExceptionStack& e) {
        e.add("operator>>(TimeStamp)", __FILE__, __LINE__);
        throw;
    }
    return is;
}

void logFunc(const char *fmt, va_list args)
{
    vprintf(fmt, args);
};

/* end of file */
