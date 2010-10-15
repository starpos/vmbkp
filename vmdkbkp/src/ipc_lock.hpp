/**
 * @file
 * @brief Header file of IpcLock.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef CYBOZU_LABS_IPC_LOCK_HPP_
#define CYBOZU_LABS_IPC_LOCK_HPP_

#include <string>
#include <boost/interprocess/sync/named_mutex.hpp>
#include <boost/interprocess/sync/named_condition.hpp>
#include <boost/interprocess/sync/scoped_lock.hpp>
#include <boost/interprocess/sync/sharable_lock.hpp>
#include <boost/interprocess/shared_memory_object.hpp>
#include <boost/interprocess/mapped_region.hpp>

namespace ipc = boost::interprocess;

typedef ipc::scoped_lock<ipc::named_mutex> ExLock;
typedef boost::shared_ptr<ExLock> ExLockP;
/*
  Shared lock feature does not exist.
  
  typedef ipc::sharable_lock<ipc::named_mutex> ShLock;
  typedef boost::shared_ptr<ShLock> ShLockP;
*/

template<typename T>
class IpcSharedMemory
{
private:
    std::string name_;
    ipc::shared_memory_object shm_;
    ipc::mapped_region region_;
    T* p_;

public:
    IpcSharedMemory(const std::string& name)
        : name_(name)
        , shm_(ipc::open_or_create, name_.c_str(), ipc::read_write)
        , region_()
        , p_(0) {

        shm_.truncate(sizeof(T));
        ipc::mapped_region tmpRegion(shm_, ipc::read_write);
        region_.swap(tmpRegion);
        p_ = static_cast<T*>(region_.get_address());
    }

    ~IpcSharedMemory() {}

    T& get() { return *p_; }

    void remove() {

        ipc::shared_memory_object::remove(name_.c_str());
    }
};

/**
 * @brief Ipc lock with shared lock/unlock.
 */
class IpcLock
{
private:
    std::string name_;
    ipc::named_mutex mutex_;
    ipc::named_condition cond_;
    
    IpcSharedMemory<int> shm_;
    int& counter_;

public:
    IpcLock(const std::string& name)
        : name_(name)
        , mutex_(ipc::open_or_create, name_.c_str())
        , cond_(ipc::open_or_create, name_.c_str())
        , shm_(name_ + "_shm")
        , counter_(shm_.get()) {}

    ~IpcLock() {}

    const std::string& getName() const { return name_; }

    void lock() {
        
        ExLock lk(mutex_);
        while (counter_ != 0) {
            cond_.wait<ExLock>(lk);
        }
        counter_ = -1;
    }

    bool try_lock() {
        
        bool ret = false;
        if (mutex_.try_lock() && counter_ == 0) {

            ret = true;
            counter_ = -1;
        }
        return ret;
    }
    
    void unlock() {

        ExLock lk(mutex_);
        assert(counter_ == -1);
        counter_ = 0;
        cond_.notify_all();
    }

    void lock_sharable() {

        ExLock lk(mutex_);
        while (counter_ == -1) {
            cond_.wait<ExLock>(lk);
        }
        counter_ ++;
    }

    bool try_lock_sharable() {

        bool ret = false;
        if (mutex_.try_lock() && counter_ >= 0) {

            ret = true;
            counter_ ++;
        }
        return ret;
    }

    void unlock_sharable() {

        ExLock lk(mutex_);
        assert(counter_ > 0);
        counter_ --;
        cond_.notify_all();
    }

    void remove() {
        
        ipc::named_mutex::remove(name_.c_str());
        ipc::named_condition::remove(name_.c_str());
        shm_.remove();
    }
};

/**
 * @brief A wrapper of named_mutex.
 */
class IpcMutex
{
private:
    std::string name_;
    ipc::named_mutex mutex_;

public:
    IpcMutex(const std::string& name)
        : name_(name)
        , mutex_(ipc::open_or_create, name_.c_str()) {}
    ~IpcMutex() {}

    ipc::named_mutex& get() { return mutex_; }

    const std::string& getName() const { return name_; }
    
    void remove() { ipc::named_mutex::remove(name_.c_str()); }
};

/**
 * @brief A wrapper of named_condition.
 */
class IpcCond
{
private:
    std::string name_;
    ipc::named_condition cond_;

public:
    IpcCond(const std::string& name)
        : name_(name)
        , cond_(ipc::open_or_create, name_.c_str()) {}

    ~IpcCond() {}

    void wait(ExLock& lk) { cond_.wait<ExLock>(lk); }
    void notify_one() { cond_.notify_one(); }
    void notify_all() { cond_.notify_all(); }
    
    const std::string& getName() const { return name_; }

    void remove() const { ipc::named_condition::remove(name_.c_str()); }
};

#endif /* CYBOZU_LABS_IPC_LOCK_HPP_ */
