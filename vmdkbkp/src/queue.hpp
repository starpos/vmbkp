/**
 * @file
 * @brief Management of synchronized queue.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_QUEUE_HPP_
#define VMDKBACKUP_QUEUE_HPP_

#include <queue>
#include <boost/thread.hpp>
#include <boost/thread/xtime.hpp>

#include "macro.hpp"

typedef boost::mutex::scoped_lock Lock;

/**
 * @brief Synchronized queue.
 *
 * Ptr should be boost::shared_ptr<T>.
 * If you use boost::thread,
 * do not call thread::interrupt() and use close() instead.
 */
template<class Ptr>
class Queue
{
private:
    std::queue<Ptr> queue_;
    
    mutable boost::mutex mutex_;
    mutable boost::condition_variable cond_;

    const size_t size_; /* maximum queue size */

    bool isClosed_;

public:
    /**
     * Constructor.
     *
     * Initially the queue is open.
     */
    Queue(size_t size)
        : size_(size), isClosed_(false) {}
    /**
     * Put operation.
     *
     * Do not access the data after put() called
     * uanless the data itself is thread-safe.
     *
     * @param blk data pointer to be put.
     * @return false if the queue was closed or the thread was interrupted.
     * @exception none.
     */
    bool put(const Ptr blk) {

        if (blk.get() == NULL) {
            WRITE_LOG0("Queue::put(): blk.get() is NULL.\n");
            return false;
        }
            
        Lock lk(mutex_);
        assert(queue_.size() <= size_);
        while (! isClosed_ && queue_.size() == size_) {
            cond_.wait(lk);
        }
        if (isClosed_) { return false; }

        assert(queue_.size() < size_);
        queue_.push(blk);
        cond_.notify_one();
        return true;
    }
    /**
     * Get operation.
     *
     * Do not lock the data itself if put() caller also access the data.
     * You need not lock it when put() caller does not access it anymore.
     *
     * @param blk data reference to be got.
     * @return false if the queue is empty and closed.
     * @exception none.
     */
    bool get(Ptr& blk) {

        Lock lk(mutex_);
        while(! isClosed_ && queue_.empty()) {
            cond_.wait(lk);
        }
        /* When isClosed_ flag is on but queue_ is not empty,
           get() will work correctly. */
        if (isClosed_ && queue_.empty()) { return false; }

        assert(! queue_.empty());
        blk = queue_.front();
        queue_.pop();
        if (blk.get() == NULL) {
            WRITE_LOG0("Queue::get(): blk.get() is NULL.\n");
        }
        cond_.notify_one();
        return true;
    }
    /**
     * Open the queue.
     */
    void open() {

        Lock lk(mutex_);
        if (isClosed_) {
            isClosed_ = false;
            cond_.notify_all();
        }
    }
    /**
     * Close the queue.
     */
    void close() {

        Lock lk(mutex_);
        if (! isClosed_) {
            isClosed_ = true;
            cond_.notify_all();
        }
    }
    /**
     * Check the queue is closed or not.
     *
     * @return true if the queue has been closed.
     */
    bool isClosed() const {

        Lock lk(mutex_);
        return isClosed_;
    }
    /**
     * Check the queue is empty or not.
     * 
     * @return true if the queue is empty.
     */
    bool isEmpty() const {

        Lock lk(mutex_);
        return queue_.empty();
    }
    /**
     * Get current queue size.
     *
     * @return size of the queue.
     */
    size_t size() const {

        Lock lk(mutex_);
        return queue_.size();
    }
    /**
     * Clear queue.
     */
    void clear() {

        Lock lk(mutex_);
        while (! queue_.empty()) {
            queue_.pop();
        }
        cond_.notify_all();
    }
};

#endif /* VMDKBACKUP_QUEUE_HPP_ */
