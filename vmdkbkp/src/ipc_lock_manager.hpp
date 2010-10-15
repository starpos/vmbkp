/**
 * @file
 * @brief Header and implementation of lock manager for interprocess.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef CYBOZU_LABS_IPC_LOCK_MANAGER_HPP_
#define CYBOZU_LABS_IPC_LOCK_MANAGER_HPP_

#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <time.h>

#include <iostream>
#include <string>
#include <sstream>
#include <vector>
#include <deque>
#include <map>
#include <boost/shared_ptr.hpp>

#include "macro.hpp"
#include "ipc_mq.hpp"
#include "ipc_lock.hpp"


/**
 * MessageQueue name for locking VDDK operations among processes.
 */
#define LOCK_MANAGER_MQ_NAME "vmdkbkp_lock_manager_mq"

#define MAX_NAME_SIZE 16

#define UUID_SIZE 16
typedef std::vector<unsigned char> Uuid;
typedef boost::shared_ptr<Uuid> UuidPtr;

inline UuidPtr generateUuid() {

    UuidPtr uuidP = UuidPtr(new Uuid(UUID_SIZE));
    
    for (size_t i = 0; i < UUID_SIZE; i ++) {
        (*uuidP)[i] = static_cast<unsigned char>(256.0 * ::rand() / (RAND_MAX + 1.0));
    }
    return uuidP;
}

/**
 * @brief Lock type.
 */
typedef enum {
    EX_LOCK = 1, SH_LOCK = 2, EX_UNLOCK = 3, SH_UNLOCK = 4
} LockType;

/**
 * @brief Serialized lock request generated/parsed with LockRequest class.
 */
struct SerializedLockRequest
{
    char type;
    char name[MAX_NAME_SIZE + 1];
    unsigned char uuid[UUID_SIZE];
};

typedef boost::shared_ptr<SerializedLockRequest> SerializedLockRequestPtr;


#define MUTEX_SUFFIX "_mutex"
#define COND_SUFFIX "_cond"
#define SHM_SUFFIX "_shm"

/**
 * @brief Ack of lock request.
 *
 * Client calls wait() to wait for ack for the request from the server.
 * Server calls notify() to notify ack to the client.
 */
class LockAck
{
private:
    std::string uuid_;
    bool isClient_;

    IpcMutex mutex_;
    IpcCond cond_;
    ExLockP lockP_;
    IpcSharedMemory<bool> shm_;
    bool& isAck_;

public:
    LockAck(const std::string& uuid, bool isClient)
        : uuid_(uuid)
        , isClient_(isClient)
        , mutex_(uuid_ + MUTEX_SUFFIX)
        , cond_(uuid_ + COND_SUFFIX)
        , shm_(uuid_ + SHM_SUFFIX)
        , isAck_(shm_.get()) {

        if (isClient_) { init(); }
    }

    ~LockAck() { if (isClient_) { remove(); }}
    
    void wait() {

        if (isClient_ && lockP_.get() == NULL) {
            lockP_ = ExLockP(new ExLock(mutex_.get()));
            while (! isAck_) {
                cond_.wait(*lockP_);
            }
            lockP_.reset();
        }
    }

    void notify() {

        if (! isClient_ && lockP_.get() == NULL) {
            lockP_ = ExLockP(new ExLock(mutex_.get()));
            isAck_ = true;

            cond_.notify_one();
            
            lockP_.reset();
        }
    }

private:
    void init() {

        if (isClient_) { isAck_ = false; }
    }
    
    void remove() {

        if (isClient_) {
            mutex_.remove();
            cond_.remove();
            shm_.remove();
        }
    }
};

/**
 * @brief Lock request management.
 */
class LockRequest
{
private:
    LockType type_;
    std::string name_; /* resource name */
    UuidPtr uuidP_; /* uuid of the request to send ack message */

public:
    LockRequest(const std::string& name, LockType type)
        : type_(type)
        , name_(name)
        , uuidP_() {

        /* assign name. */
        if (name_.length() >= MAX_NAME_SIZE) {
            throw std::string("name size is too large.");
        }

        /* generate and assign uuid. */
        uuidP_ = generateUuid();
        assert (uuidP_.get() != NULL && uuidP_->size() == UUID_SIZE);
    }

    LockRequest(const SerializedLockRequest& sReq) { /* deserialized */
        
        type_ = static_cast<LockType>(sReq.type);
        name_ = sReq.name;
        uuidP_ = UuidPtr(new Uuid(UUID_SIZE));
        ::memcpy(&(*uuidP_)[0], sReq.uuid, UUID_SIZE);
    }

    LockType getType() const { return type_; }
    std::string getTypeStr() const {

        std::string ret;
        switch (getType()) {
        case EX_LOCK:   ret = "EX_LOCK"; break;
        case SH_LOCK:   ret = "SH_LOCK"; break;
        case EX_UNLOCK: ret = "EX_UNLOCK"; break;
        case SH_UNLOCK: ret = "SH_UNLOCK"; break;
        default:
            throw std::string("lock type error.");
        }
        return ret;
    }
    const std::string& getName() const { return name_; }
    const std::vector<unsigned char>& getUuid() const { return *uuidP_; }
    std::string getUuidStr() const {

        const std::vector<unsigned char>& uuid = getUuid();

        std::stringstream ss;
        for (std::vector<unsigned char>::const_iterator i = uuid.begin();
             i != uuid.end(); ++ i) {

            int c = *i;
            int upper = c / 16;
            int lower = c % 16;

            ss << int2char(upper)
               << int2char(lower);
        }
        return ss.str();
    }

    bool isLockReq() const {

        LockType t = getType();
        return (t == EX_LOCK || t == SH_LOCK);
    }

    bool isExclusive() const {

        LockType t = getType();
        return (t == EX_LOCK || t == EX_UNLOCK);
    }

    void serialize(SerializedLockRequest& sReq) const {

        sReq.type = static_cast<char>(type_);
        assert(name_.length() <= MAX_NAME_SIZE);
        ::strcpy(sReq.name, name_.c_str());
        ::memcpy(sReq.uuid, &(*uuidP_)[0], UUID_SIZE);
    }

    std::string toString() const {

        return getTypeStr() + " " + getName() + "(" + getUuidStr() + ")";
    }

private:
    char int2char(int i) const {

        return (i < 10 ? static_cast<char>(i) + '0'
                : static_cast<char>(i - 10) + 'a');
    }
};

typedef boost::shared_ptr<LockRequest> LockRequestPtr;

/**
 * @brief Lock manager server.
 */
class LockManagerServer
{
private:
    const bool isClient_;

    typedef std::deque<LockRequestPtr> LockRequestQueue;
    typedef boost::shared_ptr<LockRequestQueue> LockRequestQueuePtr;
    
    /**
     * Temporary queue for each resource.
     * Map from resource name to deque ptr.
     */
    std::map<std::string, LockRequestQueuePtr> reqDeqMap_;

    /**
     * Map from resource name to counter.
     *   counter 0: unlocked.
     *   counter -1: exclusive locked.
     *   counter >0 : shared locked.
     */
    std::map<std::string, int> counterMap_;
    
public:

    LockManagerServer() : isClient_(false) {}
    ~LockManagerServer() {}

    /**
     * @brief proocess lock/unlock request.
     * 
     */
    void processRequest(LockRequestPtr reqP) {

        assert (reqP.get() != NULL);
        LockRequestQueue& reqDeq = searchRequestDeque(reqP);
            
        if (reqP->isLockReq()) {

            reqDeq.push_front(reqP);
        } else {
            /* Process unlock immediately. */
            if (! tryExecRequest(reqP)) {
                WRITE_LOG0("execute request failed lock %s %s.\n",
                           reqP->getTypeStr().c_str(),
                           reqP->getName().c_str());
            }

            /* Currently reply ack even if unlock failed. */
            LockAck ack(reqP->getUuidStr(), isClient_);
            ack.notify();
        }

        /* Process lock while possible in line. */
        bool isSucceeded = true;
        while (isSucceeded && ! reqDeq.empty()) {

            if (tryExecRequest(reqDeq.back())) {

                LockAck ack((reqDeq.back())->getUuidStr(), isClient_);
                ack.notify();

                reqDeq.pop_back();
            } else {
                isSucceeded = false;
            }
        }
    }

    void print() const {

        std::cout << "[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[\n";
        
        for (std::map<std::string, int>::const_iterator i
                 = counterMap_.begin();
             i != counterMap_.end(); ++ i) {

            std::cout << i->first << ":"
                      << i->second << "\n";

            std::map<std::string, LockRequestQueuePtr>::const_iterator it =
                reqDeqMap_.find(i->first);
            assert (it != reqDeqMap_.end());
            LockRequestQueuePtr reqDeqP = it->second;
            assert (reqDeqP.get() != NULL);
            for (LockRequestQueue::const_iterator j = reqDeqP->begin();
                 j != reqDeqP->end(); ++ j) {

                std::cout << "\t" << (*j)->toString() << std::endl;
            }
        }
        std::cout << "]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]]\n";
    }

private:
    LockRequestQueue& searchRequestDeque(LockRequestPtr reqP) {

        assert(reqP.get() != NULL);
        std::string resourceName = reqP->getName();
        LockRequestQueuePtr queP = reqDeqMap_[resourceName];

        /* Generate deque if not found. */
        if (queP.get() == NULL) {
            queP = LockRequestQueuePtr(new LockRequestQueue);
            reqDeqMap_[resourceName] = queP;
        }
        assert (queP.get() != NULL);
        return *queP;
    }
    
    bool tryExecRequest(LockRequestPtr reqP) {

        assert(reqP.get() != NULL);
        const std::string& name = reqP->getName();
        int counter = counterMap_[name];

        bool ret = false;
        switch (reqP->getType()) {
        case EX_LOCK:
            if (counter == 0) { counter = -1; ret = true; } break;
        case SH_LOCK:
            if (counter >= 0) { counter ++; ret = true; } break;
        case EX_UNLOCK:
            if (counter == -1) { counter = 0; ret = true; } break;
        case SH_UNLOCK:
            if (counter > 0) { counter --; ret = true; } break;
        default:
            throw std::string("lock type error.");
        }

        if (ret) { counterMap_[name] = counter; }
        return ret;
    }
};

/**
 * @brief Lock manager client.
 */
class LockManagerClient
{
private:
    const bool isClient_;
    IpcMessageQueue<SerializedLockRequest> mq_;
    std::string resourceName_;
    
public:
    LockManagerClient(
        const std::string& mqName,
        const std::string& resourceName)
        : isClient_(true)
        , mq_(mqName)
        , resourceName_(resourceName) {}

    ~LockManagerClient() {}

    static void srand() { ::srand(::time(0) + ::getpid()); }

    void lock() {

        bool isExclusive = true;
        lockImpl(isExclusive);
    }

    void lock_sharable() {

        bool isExclusive = false;
        lockImpl(isExclusive);
    }

    void unlock() {

        bool isExclusive = true;
        unlockImpl(isExclusive);
    }

    void unlock_sharable() {

        bool isExclusive = false;
        unlockImpl(isExclusive);
    }
    
private:
    void lockImpl(bool isExclusive) {

        LockRequest req(resourceName_,
                        (isExclusive ? EX_LOCK : SH_LOCK));

        if (0) {
            std::cout << "Client: send request: "
                      << req.toString() << std::endl;
        }
        
        enqueueRequestAndWait(req);
    }

    void unlockImpl(bool isExclusive) {

        LockRequest req(resourceName_,
                        (isExclusive? EX_UNLOCK : SH_UNLOCK));

        if (0) {
            std::cout << "Client: send request: "
                      << req.toString() << std::endl;
        }
        
        enqueueRequestAndWait(req);
    }

    void enqueueRequestAndWait(const LockRequest& req) {

        SerializedLockRequest sReq;
        req.serialize(sReq);

        LockAck ack(req.getUuidStr(), isClient_);
        
        if (! mq_.put(sReq)) {
            std::string reqStr =
                std::string("lock request put failed. ") + req.toString();
            WRITE_LOG0("%s\n", reqStr.c_str());
            throw reqStr;
        }
        
        ack.wait();
    }
};


/**
 * @brief Scoped resource lock.
 *
 * Resource is specified with a name.
 * You can also use shared lock.
 * You must start lock server before using this instance.
 */
class ScopedResourceLock
{
private:
    LockManagerClient lockMgr_;
    bool isExclusive_;
    
public:
    ScopedResourceLock(const std::string& resourceName, bool isExclusive)
        : lockMgr_(LOCK_MANAGER_MQ_NAME, resourceName)
        , isExclusive_(isExclusive) {

        lock();
    }
    ~ScopedResourceLock() { unlock(); }
    
private:
    void lock() {

        if (isExclusive_) {
            lockMgr_.lock();
        } else {
            lockMgr_.lock_sharable();
        }
    }
    void unlock() {

        if (isExclusive_) {
            lockMgr_.unlock();
        } else {
            lockMgr_.unlock_sharable();
        }
    }
};

#endif /* CYBOZU_LABS_IPC_LOCK_MANAGER_HPP_ */
