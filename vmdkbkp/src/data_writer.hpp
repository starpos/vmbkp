/**
 * @file
 * @brief Definition and implementation of DataWriter.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_DATA_WRITER_HPP_
#define VMDKBACKUP_DATA_WRITER_HPP_

#include <iostream>

#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>

#include "queue.hpp"
#include "exception.hpp"
#include "macro.hpp"

#define WRITER_QUEUE_SIZE 16

/**
 * @brief Shared data with thread master and worker for DataWriter.
 */
template<typename T>
class WriteWorkerData
{
    typedef boost::shared_ptr<T> TPtr;
    std::ostream* osP_;
    Queue<TPtr>* queueP_;

    /* initialization flag (worker -> master) */
    volatile bool isInitialized_;
    /* flag that put() will be no more called. (master -> worker) */
    volatile bool isStoppedQueuing_;

public:
    /**
     * Constructor.
     */
    WriteWorkerData(std::ostream* osP, Queue<TPtr>* queueP)
        : osP_(osP)
        , queueP_(queueP)
        , isInitialized_(false)
        , isStoppedQueuing_(false) {}
    /**
     * Get reference of the queue.
     */
    Queue<TPtr>& getQueue() const {
        return *queueP_;
    }
    /**
     * Dequeue data and write it to the output stream.
     */
    void dequeueAndWrite() {

        TPtr dataP;
        if (queueP_->get(dataP)) { 
            *osP_ << *dataP;
        }
    }
    /**
     * Reset the flags.
     */
    void reset() {
        isInitialized_ = false;
        isStoppedQueuing_ = false;
    }
    /**
     * Set isInitialized_ flag true.
     * Call this when initialization ends.
     */
    void initialized() {
        isInitialized_ = true;
    }
    /**
     * Wait initialized_ flag becomes true.
     */
    void waitInitialized() const {
        while (! isInitialized_) {}
    }
    /**
     * Stop enqueueing.
     */
    void stopQueuing() {
        isStoppedQueuing_ = true;
    }
    /**
     * Check the flag.
     */
    bool isStoppedQueuing() const {
        return isStoppedQueuing_;
    }
};

/**
 * @brief Worker thread for DataWriter.
 */
template<typename T>
void writeWorker(WriteWorkerData<T>* wwdP)
{
    typedef boost::shared_ptr<T> TPtr;

    /* initialize */
    WriteWorkerData<T>& wwd = *wwdP;
    Queue<TPtr>& queue = wwd.getQueue();
    wwd.initialized();

    /* write data */
    try {
        while (! queue.isEmpty() || ! wwd.isStoppedQueuing()) {
            wwd.dequeueAndWrite();
        }

    } catch (const ExceptionStack& e) {
        WRITE_LOG0("writeWorker: exception %s\n", e.sprint().c_str());
        queue.close();
    }
    WRITE_LOG1("writerWorker finished\n"); /* debug */
}

/**
 * @brief FIFO data writer in parallel.
 * 
 * typename T must have operator<<() member function.
 * Output stream will write data of type T repeatedly.
 */
template<typename T>
class DataWriter
{
private:
    typedef boost::shared_ptr<T> TPtr;
    typedef boost::thread Thread;
    typedef boost::shared_ptr<Thread> ThreadPtr;
    
    /* Output stream (reference) */
    std::ostream& os_;
    
    /* Data queue */
    Queue<TPtr> queue_;

    /* Thread pointer */
    ThreadPtr th_;

    /* Shared with worker thread. */
    WriteWorkerData<T> wwd_;

    /* Pause flag */
    bool isPaused_;
    
public:
    /**
     * Constructor.
     */
    DataWriter(std::ostream& os,
               size_t queueSize = WRITER_QUEUE_SIZE)
        : os_(os)
        , queue_(queueSize)
        , wwd_(&os_, &queue_)
        , isPaused_(false) {}
    /**
     * Destructor.
     */
    ~DataWriter() {

        stop();
    }
    /**
     * Create and start worker thread.
     */
    void start() {
        
        if (th_.get() == NULL) {
            wwd_.reset();
            queue_.open();
            th_ = ThreadPtr(new Thread(writeWorker<T>, &wwd_));
            wwd_.waitInitialized();
        } else {
            WRITE_LOG1("start() called while th_ is not null.\n");
        }
    }
    /**
     * Stop the worker thread.
     */
    void stop() {

        if (th_.get() != NULL) {

            /* Notify the worker thread of
               put() will no more be called so the worker
               write remaining items in the queue and return. */
            wwd_.stopQueuing();
            
            queue_.close();
            th_->join();
            th_.reset();
        } else {
            WRITE_LOG1("stop() called while th_ is null.\n");
        }
    }
    /**
     * Put data to the queue.
     * This is blocking method.
     *
     * @return false when the queue has finished already and
     * temporary buffer is full.
     */
    bool put(const TPtr ptr) {
        
        return queue_.put(ptr);
    }
    /**
     * Pause the reader by finishing thee worker thread.
     *
     * You must call this after calling start() or resume().
     */
    void pause() {

        assert(! isPaused_);
        stop();
        isPaused_ = true;
    }
    /**
     * Resume the reader by starting the worker thread.
     *
     * You must call this after calling pause().
     */
    void resume() {

        assert(isPaused_);
        start();
        isPaused_ = false;
    }
};

#endif /* VMDKBACKUP_DATA_WRITER_HPP_ */
