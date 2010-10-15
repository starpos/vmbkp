/**
 * @file
 * @brief Header file of IpcMessageQueue.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef CYBOZU_LABS_IPC_MESSAGE_QUEUE_HPP_
#define CYBOZU_LABS_IPC_MESSAGE_QUEUE_HPP_

#include <string>
#include <boost/shared_ptr.hpp>
#include <boost/interprocess/ipc/message_queue.hpp>

#include "macro.hpp"

namespace ipc = boost::interprocess;

template<typename T>
class IpcMessageQueue
{
private:
    std::string name_;
    const bool isServer_;
    boost::shared_ptr<ipc::message_queue> mqP_;

public:
    IpcMessageQueue(const std::string& name, size_t size)
        : name_(name)
        , isServer_(true) {

        remove();
        mqP_ = boost::shared_ptr<ipc::message_queue>
            (new ipc::message_queue
             (ipc::open_or_create, name_.c_str(), size, sizeof(T)));
    }
    
    IpcMessageQueue(const std::string& name)
        : name_(name)
        , isServer_(false) {

        mqP_ = boost::shared_ptr<ipc::message_queue>
            (new ipc::message_queue(ipc::open_only, name_.c_str()));
    }
    
    ~IpcMessageQueue() { if (isServer_) { remove(); } }
    
    bool put(T& t) {

        try {
            mqP_->send(&t, sizeof(T), 0);
            return true;

        } catch (ipc::interprocess_exception& e) {
            WRITE_LOG0("put(): %s\n", e.what());
            return false;
        }
    }

    bool tryPut(T& t) {

        return mqP_->try_send(&t, sizeof(T), 0);
    }
    
    bool get(T& t) {

        try {
            size_t sz;
            unsigned int pri;
            mqP_->receive(&t, sizeof(T), sz, pri);
            return true;
            
        } catch (ipc::interprocess_exception& e) {
            WRITE_LOG0("get(): %s\n", e.what());
            return false;
        }
    }

    bool tryGet(T& t) {

        size_t sz;
        unsigned int pri;
        return mqP_->try_receive(&t, sizeof(T), sz, pri);
    }

private:
    void remove() {

        ipc::message_queue::remove(name_.c_str());
    }
};

#endif /* CYBOZU_LABS_IPC_MESSAGE_QUEUE_HPP_ */
