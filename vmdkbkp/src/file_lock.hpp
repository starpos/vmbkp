/**
 * @file
 * @brief ScopedFileLock definition and implementation.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_FILE_LOCK_HPP_
#define VMDKBACKUP_FILE_LOCK_HPP_

#include <iostream>
#include <fstream>
#include <string>

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#include <boost/shared_ptr.hpp>
#include <boost/interprocess/sync/file_lock.hpp>

/**
 * @brief Wrapper class of boost file_lock.
 *
 * This class uses boost file_lock, so the limitation is inherited.
 *   1. Do not make multiple instance for the same lockfile in a process.
 *   2. Do not access from different threads.
 *
 * The first limitation is ignorable for non-Windows system
 * because they use just fcntl system call to lock/unlock.
 * However, this implementation wastes file descripters.
 */
class ScopedFileLock
{
private:
    std::string filename_;
    bool isExclusive_;
    bool isLocked_;

    typedef boost::interprocess::file_lock FileLock;
    typedef boost::shared_ptr<FileLock> FileLockPtr;
    FileLockPtr flockP_;
    
public:
    /**
     * Lock in constructor.
     *
     * @param filename Lock file name. Create the file if not exists.
     * @param isExclusive If exclusive lock, default is true.
     */
    ScopedFileLock(const std::string& filename, bool isExclusive = true)
        : filename_(filename), isExclusive_(isExclusive), isLocked_(false) {
        
        struct stat fileStat;
        if (::stat(filename_.c_str(), &fileStat) != 0) {
            ::printf("create file\n");
            std::ofstream os(filename_.c_str(), std::ios_base::trunc);
        }

        flockP_ = FileLockPtr(new FileLock(filename_.c_str()));
        lock();
    }
    /**
     * Unlock in destructor.
     */
    ~ScopedFileLock() {
        
        unlock();
        flockP_.reset();
    }
        
private:
    void lock() {

        if (! isLocked_) {
            if (isExclusive_) {
                flockP_->lock();
            } else {
                flockP_->lock_sharable();
            }
            isLocked_ = true;
        }
    }
    void unlock() {

        if (isLocked_) {
            if (isExclusive_) {
                flockP_->unlock();
            } else {
                flockP_->unlock_sharable();
            }
            isLocked_ = false;
        }
    }
};

#endif /* VMDKBACKUP_FILE_LOCK_HPP_ */
