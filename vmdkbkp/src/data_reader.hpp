/**
 * @file
 * @brief Definition and implementation of DataReader.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_DATA_READER_HPP_
#define VMDKBACKUP_DATA_READER_HPP_

#include <iostream>

#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>

#include "queue.hpp"
#include "generator.hpp"
#include "exception.hpp"
#include "macro.hpp"

#define READER_QUEUE_SIZE 16

/**
 * @brief Shared data with thread master and worker for DataReader.
 */
template<typename T>
class ReadWorkerData
{
private:
    typedef boost::shared_ptr<T> TPtr;
    std::istream* isP_;
    Queue<TPtr>* queueP_;
    TPtr tmpDataP_; /* temporal input data for pause. */

    /* Data generator of T */
    typedef boost::shared_ptr<Generator<T> > GenP;
    GenP generatorP_;

    /* initialization flag (worker -> master) */
    volatile bool isInitialized_;
    /* flag that input have been all consumed. (worker -> master) */
    volatile bool isEnd_;
public:
    /**
     * Constructor.
     */
    ReadWorkerData(std::istream* isP,
                   Queue<TPtr>* queueP,
                   GenP generatorP)
        : isP_(isP)
        , queueP_(queueP)
        , tmpDataP_()
        , generatorP_(generatorP)
        , isInitialized_(false)
        , isEnd_(false) {}
    /**
     * Get reference of the queue.
     */
    Queue<TPtr>& getQueue() const {
        return *queueP_;
    }
    /**
     * Read data from the input stream and enqueue it.
     */
    void readAndEnqueue() {

        TPtr dataP;
        read(dataP);
        enqueue(dataP);
    }
    /**
     * Check input stream reaches end of file.
     * @return True if isP_ reaches end of file.
     */
    bool isEOF() const {
        return (isP_->peek() == EOF);
    }
    /**
     * Reset the flags.
     */
    void reset() {
        isInitialized_ = false;
        isEnd_ = false;
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
     * Set isEnd_ flag when input stream reachs end.
     */
    void end() {
        isEnd_ = true;
    }
    /**
     * End flag is on or off.
     */
    bool isEnd() const {
        return isEnd_;
    }

private:
    /**
     * Read data from the input stream.
     * This must be called by worker.
     */
    void read(TPtr& dataP) {

        Generator<T>& generator = *generatorP_;
        if (tmpDataP_.get() == NULL) {
            dataP = TPtr(generator());
            *isP_ >> *dataP;
        } else {
            dataP = tmpDataP_;
            tmpDataP_.reset();
        }
    }
    /**
     * Enqueue the specified item.
     * This must be called by worker.
     */
    void enqueue(TPtr& dataP) {

        if (! queueP_->put(dataP)) {
            tmpDataP_ = dataP;
        }
    }
};

/**
 * @brief Worker thread for DataReader.
 */
template<typename T>
void readWorker(ReadWorkerData<T>* rwdP)
{
    typedef boost::shared_ptr<T> TPtr;

    /* Initialize. */
    ReadWorkerData<T>& rwd = *rwdP;
    Queue<TPtr>& queue = rwd.getQueue();
    rwd.initialized();

    /* Read data. */
    try {
        while (! rwd.isEOF() && ! queue.isClosed()) {
            rwd.readAndEnqueue();
        }
        rwd.end();

    } catch (const ExceptionStack& e) {
        WRITE_LOG0("readWorker: exception %s\n", e.sprint().c_str());
        rwd.end(); queue.close();
    }
    WRITE_LOG1("readWorker finished\n"); /* debug */
}

/**
 * @brief FIFO data reader in parallel.
 * 
 * typename T must have operator>>() member function.
 * Input stream must have repeated data of type T until end.
 */
template<typename T>
class DataReader
{
    typedef boost::shared_ptr<Generator<T> > GenP;
private:
    typedef boost::shared_ptr<T> TPtr;
    typedef boost::thread Thread;
    typedef boost::shared_ptr<Thread> ThreadPtr;
    
    /* Input stream (reference) */
    std::istream& is_;
    
    /* Data queue */
    Queue<TPtr> queue_;

    /* Thread pointer */
    ThreadPtr th_;

    /* Shared with worker thread. */
    ReadWorkerData<T> rwd_;

    /* Pause flag */
    bool isPaused_;
    
public:
    /**
     * Constructor.
     */
    DataReader(std::istream& is,
               GenP generatorP,
               size_t queueSize = READER_QUEUE_SIZE)
        : is_(is)
        , queue_(queueSize)
        , rwd_(&is_, &queue_, generatorP)
        , isPaused_(false) {}
    /**
     * Destructor.
     */
    ~DataReader() {

        stop();
    }
    /**
     * Create and start worker thread.
     */
    void start() {

        if (th_.get() == NULL) {
            rwd_.reset();
            queue_.open();
            th_ = ThreadPtr(new Thread(readWorker<T>, &rwd_));
            rwd_.waitInitialized();
        } else {
            WRITE_LOG1("start() called while th_ is not null.\n");
        }
    }
    /**
     * Stop the worker thread.
     */
    void stop() {

        if (th_.get() != NULL) {
            queue_.close();
            th_->join();
            th_.reset();
        } else {
            WRITE_LOG1("stop() called while th_ is null.\n");
        }
    }
    /**
     * Check whether all input data has been consumed.
     *    
     * @return True when the input stream ends and queue is empty.
     */
    bool isEnd() const {
        
        return rwd_.isEnd() && queue_.isEmpty();
    }
    /**
     * Get data from queue.
     * This is blocking method.
     *
     * @return data pointer or TPtr() which means NULL.
     */
    TPtr get() {

        TPtr ret;
        queue_.get(ret);
        return ret;
    }
    /**
     * Pause the reader by finishing the worker thread.
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

#endif /* VMDKBACKUP_DATA_READER_HPP_ */
