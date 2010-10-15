/**
 * @file
 * @brief Archive IO Managers.
 *
 * <pre>
 * Inteface:
 *   DumpInManager, DigestInManager, DumpOutManager, DigestOutManager.
 * Implementation:
 *   ParallelDumpInManager, ParallelDigestInManager,
 *   ParallelDumpOutManager, ParallelDigestOutManager.
 *   SingleDumpInManager, SingleDigestInManager,
 *   SingleDumpOutManager, SingleDigestOutManager.
 * </pre>
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_ARCHIVE_IO_MANAGER_HPP_
#define VMDKBACKUP_ARCHIVE_IO_MANAGER_HPP_

#include <boost/shared_ptr.hpp>
#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/filter/gzip.hpp>
#include <boost/iostreams/device/file.hpp>

#include "header.hpp"
#include "data_reader.hpp"
#include "data_writer.hpp"

namespace io = boost::iostreams;

typedef boost::shared_ptr<VmdkDumpHeader>   DumpHP;
typedef boost::shared_ptr<VmdkDumpBlock>    DumpBP;
typedef boost::shared_ptr<VmdkDigestHeader> DigestHP;
typedef boost::shared_ptr<VmdkDigestBlock>  DigestBP;

typedef DataReader<VmdkDumpBlock>   DumpReader;
typedef DataWriter<VmdkDumpBlock>   DumpWriter;
typedef DataReader<VmdkDigestBlock> DigestReader;
typedef DataWriter<VmdkDigestBlock> DigestWriter;

typedef boost::shared_ptr<DumpReader>   DumpReaderP;
typedef boost::shared_ptr<DumpWriter>   DumpWriterP;
typedef boost::shared_ptr<DigestReader> DigestReaderP;
typedef boost::shared_ptr<DigestWriter> DigestWriterP;

/**
 * @brief Interface of dump input manager.
 *
 * <pre>
 * init() -> start()
 * -> getB() ... -> pause() -> resume()
 * -> getB() ... -> pause() -> resume()
 * ...
 * -> getB() ... -> stop().
 * </pre>
 * You can call getH() after init().
 */
class DumpInManager
{
public:
    /**
     * Constructor.
     */
    DumpInManager() {}
    /**
     * Destructor.
     */
    virtual ~DumpInManager() {}
    /**
     * Initializer.
     *
     * @param dumpInFn file name of dump input.
     */
    virtual void init(const std::string& dumpInFn) = 0;
    /**
     * @return True if initialization has finished.
     */
    virtual bool isInit() const = 0;
    /**
     * Get dumpH data.
     *
     * @return shared_ptr of header data.
     * This is shared with this object, so do not modify it.
     */
    virtual const DumpHP getH() const = 0;
    /**
     * Get block data.
     *
     * @return shared_ptr of block data.
     * This is not shared with this object.
     */
    virtual DumpBP getB() = 0;
    /**
     * @return True if the all input data have been consumed.
     */
    virtual bool isEnd() = 0;
    /**
     * Start the manager.
     */
    virtual void start() = 0;
    /**
     * Stop the manager.
     */
    virtual void stop() = 0;
    /**
     * Pause related threads if exist to call fork().
     */
    virtual void pause() = 0;
    /**
     * Resume related threads if called after pause().
     */
    virtual void resume() = 0;
};

/**
 * @brief Multi-threaded implementation of DumpInManager.
 */
class ParallelDumpInManager
    : virtual public DumpInManager
{
    /* Generator. */
    typedef Generator1<VmdkDumpBlock, size_t> Gen;
    /* Generator pointer. */
    typedef boost::shared_ptr<Gen> GenP;
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_istream is_;
    DumpHP dumpHP_;
    DumpReaderP readerP_;

public:
    ParallelDumpInManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& dumpInFn) {

        WRITE_LOG1("ParallelDumpInManager::init() called.\n");
        if (isGzipFileName(dumpInFn)) {
            is_.push(io::gzip_decompressor());
        }
        is_.push(io::file_source(dumpInFn));

        dumpHP_ = DumpHP(new VmdkDumpHeader);
        is_ >> *dumpHP_;

        assert(dumpHP_.get() != NULL);
        size_t bs = dumpHP_->getBlockSize();
        GenP genP = GenP(new Gen(bs));
        readerP_ = DumpReaderP(new DumpReader(is_, genP));
        assert(readerP_.get() != NULL);
        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    const DumpHP getH() const {

        assert(isInit_);
        return dumpHP_;
    }
    DumpBP getB() {

        assert(isStarted_ && readerP_.get() != NULL);
        DumpBP dumpBP = readerP_->get();
        if (dumpBP.get() == NULL) {
            throw ExceptionStack("readerP_->get() is NULL",
                                 __FILE__, __LINE__);
        }
        return dumpBP;
    }
    bool isEnd() {

        assert(isInit_ && readerP_.get() != NULL);
        return readerP_->isEnd();
    }
    void start() {

        WRITE_LOG1("ParallelDumpInManager::start() called.\n");
        assert(isInit_);
        readerP_->start();
        isStarted_ = true;
    }
    void stop() {
        
        WRITE_LOG1("ParallelDumpInManager::stop() called.\n");
        assert(isStarted_ && readerP_.get() != NULL);
        readerP_->stop();
        is_.reset();
    }
    void pause() {

        WRITE_LOG1("ParallelDumpInManager::pause() called.\n");
        assert(isStarted_ && readerP_.get() != NULL);
        readerP_->pause();
    }
    void resume() {

        WRITE_LOG1("ParallelDumpInManager::resume() called.\n");
        assert(isStarted_ && readerP_.get() != NULL);
        readerP_->resume();
    }
};

/**
 * @brief Single-threaded implementation of DumpInManager.
 */
class SingleDumpInManager
    : virtual public DumpInManager
{
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_istream is_;
    DumpHP dumpHP_;
    size_t blocksize_;
    
public:
    SingleDumpInManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& dumpInFn) {

        if (isGzipFileName(dumpInFn)) {
            is_.push(io::gzip_decompressor());
        }
        is_.push(io::file_source(dumpInFn));

        dumpHP_ = DumpHP(new VmdkDumpHeader);
        is_ >> *dumpHP_;

        assert(dumpHP_.get() != NULL);
        blocksize_ = dumpHP_->getBlockSize();
        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    const DumpHP getH() const {

        assert(isInit_);
        return dumpHP_;
    }
    DumpBP getB() {

        assert(isStarted_);
        DumpBP dumpBP = DumpBP(new VmdkDumpBlock(blocksize_));
        is_ >> *dumpBP;
        return dumpBP;
    }
    bool isEnd() { /* filtering_istream::peek() is not const. */

        assert(isInit_);
        return is_.peek() == EOF;
    }
    void start() {

        assert(isInit_);
        isStarted_ = true;
    }
    void stop() {

        assert(isStarted_);
        is_.reset();
    }
    void pause() {}
    void resume() {}
};

/**
 * @brief Interface of digest input manager.
 *
 * <pre>
 * init() -> start()
 * -> getB() ... -> pause() -> resume()
 * -> getB() ... -> pause() -> resume()
 * ...
 * -> getB() ... -> stop().
 * </pre>
 * You can call getH() after init().
 */
class DigestInManager
{
public:
    /**
     * Constructor.
     */
    DigestInManager() {}
    /**
     * Destructor.
     */
    virtual ~DigestInManager() {}
    /**
     * Initializer.
     *
     * @param digestInFn file name of digest input.
     */
    virtual void init(const std::string& digestInFn) = 0;
    /**
     * @return True if initialization finished.
     */
    virtual bool isInit() const = 0;
    /**
     * @return True if all input data have been consumed.
     */
    virtual bool isEnd() = 0;
    /**
     * Get digestH data.
     *
     * @return shared_ptr of digest data.
     * This is shared with this object, so do not modify it.
     */
    virtual const DigestHP getH() const = 0;
    /**
     * Get digest data.
     *
     * @return shared_ptr of digest data.
     * This is not shared with this object.
     */
    virtual DigestBP getB() = 0;
    /**
     * Start the manager.
     */
    virtual void start() = 0;
    /**
     * Stop the manager.
     */
    virtual void stop() = 0;
    /**
     * Pause related threas if exists to call fork().
     */
    virtual void pause() = 0;
    /**
     * Resume realted thread if called after pause().
     */
    virtual void resume() = 0;
};

/**
 * @brief Multi-threaded implementation of DigestInManager.
 */
class ParallelDigestInManager
    : virtual public DigestInManager
{
    /* Generator */
    typedef Generator0<VmdkDigestBlock> Gen;
    /* Generator pointer */
    typedef boost::shared_ptr<Gen> GenP;
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_istream is_;
    DigestHP digestHP_;
    DigestReaderP readerP_;
public:
    ParallelDigestInManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& digestInFn) {

        WRITE_LOG1("ParallelDigestInManager::init() called.\n");
        if (isGzipFileName(digestInFn)) {
            is_.push(io::gzip_decompressor());
        }
        is_.push(io::file_source(digestInFn));

        digestHP_ = DigestHP(new VmdkDigestHeader);
        is_ >> *digestHP_;

        GenP genP = GenP(new Gen);
        readerP_ = DigestReaderP(new DigestReader(is_, genP));
        assert(readerP_.get() != NULL);
        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    bool isEnd() {

        assert(isInit_ && readerP_.get() != NULL);
        return readerP_->isEnd();
    }
    const DigestHP getH() const {

        assert(isInit_);
        return digestHP_;
    }
    DigestBP getB() {

        assert(isStarted_ && readerP_.get() != NULL);
        DigestBP digestBP = readerP_->get();
        if (digestBP.get() == NULL) {
            throw ExceptionStack("readerP_->get() is NULL",
                                 __FILE__, __LINE__);
        }
        return digestBP;
    }
    void start() {

        WRITE_LOG1("ParallelDigestInManager::start() called.\n");
        assert(isInit_);
        readerP_->start();
        isStarted_ = true;
    }
    void stop() {

        WRITE_LOG1("ParallelDigestInManager::stop() called.\n");
        assert(isStarted_ && readerP_.get() != NULL);
        readerP_->stop();
        is_.reset();
    }
    void pause() {

        WRITE_LOG1("ParallelDigestInManager::pause() called.\n");
        assert(readerP_.get() != NULL);
        readerP_->pause();
    }
    void resume() {

        WRITE_LOG1("ParallelDigestInManager::resume() called.\n");
        assert(readerP_.get() != NULL);
        readerP_->resume();
    }
};

/**
 * @brief Single-threaded implementation of DigestInManager.
 */
class SingleDigestInManager
    : virtual public DigestInManager
{
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_istream is_;
    DigestHP digestHP_;
    
public:
    SingleDigestInManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& digestInFn) {

        if (isGzipFileName(digestInFn)) {
            is_.push(io::gzip_decompressor());
        }
        is_.push(io::file_source(digestInFn));

        digestHP_ = DigestHP(new VmdkDigestHeader);
        is_ >> *digestHP_;

        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    const DigestHP getH() const {

        assert(isInit_);
        return digestHP_;
    }
    DigestBP getB() {

        assert(isInit_);
        DigestBP digestBP = DigestBP(new VmdkDigestBlock);
        is_ >> *digestBP;
        return digestBP;
    };
    bool isEnd() { /* filtering_istream::peek() is not const. */

        assert(isInit_);
        return is_.peek() == EOF;
    }
    void start() {

        assert(isInit_);
        isStarted_ = true;
    }
    void stop() {
        
        assert(isStarted_);
        is_.reset();
    }
    void pause() {}
    void resume() {}
};

/**
 * @brief Interface of dump output manager.
 *
 * init() -> putH() -> start()
 * -> putB() ... -> pause() -> resume()
 * -> putB() ... -> pause() -> resume()
 * ...
 * -> putB() ... -> stop().
 */
class DumpOutManager
{
public:
    /**
     * Constructor.
     */
    DumpOutManager() {}
    /**
     * Destructor.
     */
    virtual ~DumpOutManager() {}
    /**
     * Initialier.
     * @param dumpOutFn file name of dump output.
     */
    virtual void init(const std::string& dumpOutFn) = 0;
    /**
     * @return True if initialization has finished.
     */
    virtual bool isInit() const = 0;
    /**
     * Put header data.
     */
    virtual void putH(const DumpHP dumpHP) = 0;
    /**
     * Put block data.
     */
    virtual void putB(const DumpBP dumpBP) = 0;
    /**
     * Start the manager.
     */
    virtual void start() = 0;
    /**
     * Stop the manager.
     */
    virtual void stop() = 0;
    /**
     * Pause related thread if exists to call fork().
     */
    virtual void pause() = 0;
    /**
     * Resume related thread if called after pause().
     */
    virtual void resume() = 0;
};

/**
 * @brief Multi-threaded implementation of DumpOutManager.
 */
class ParallelDumpOutManager
    : virtual public DumpOutManager
{
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_ostream os_;
    DumpWriterP writerP_;
public:
    ParallelDumpOutManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& dumpOutFn) {

        WRITE_LOG1("ParallelDumpOutManager::init() called.\n");
        if (isGzipFileName(dumpOutFn)) {
            os_.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
        }
        os_.push(io::file_sink(dumpOutFn));

        writerP_ = DumpWriterP(new DumpWriter(os_));
        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    void putH(const DumpHP dumpHP) {

        assert(isInit_ && ! isStarted_);
        os_ << *dumpHP;
    }
    void putB(const DumpBP dumpBP) {

        assert(isInit_ && isStarted_);
        assert(writerP_.get() != NULL);
        if (! writerP_->put(dumpBP)) {
            throw ExceptionStack("writerP_->put() failed.",
                                 __FILE__, __LINE__);
        }
    }
    void start() {

        WRITE_LOG1("ParallelDumpOutManager::start() called.\n");
        assert(isInit_ && writerP_.get() != NULL);
        writerP_->start();
        isStarted_ = true;
    }
    void stop() {

        WRITE_LOG1("ParallelDumpOutManager::stop() called.\n");
        assert(isInit_ && isStarted_ && writerP_.get() != NULL);
        writerP_->stop(); os_.reset();
    }
    void pause() {

        WRITE_LOG1("ParallelDumpOutManager::pause() called.\n");
        assert(writerP_.get() != NULL);
        writerP_->pause();
    }
    void resume() {

        WRITE_LOG1("ParallelDumpOutManager::resume() called.\n");
        assert(writerP_.get() != NULL);
        writerP_->resume();
    }
};

/**
 * @brief Single-threaded implementation of DumpOutManager.
 */
class SingleDumpOutManager
    : virtual public DumpOutManager
{
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_ostream os_;
public:
    SingleDumpOutManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& dumpOutFn) {

        if (isGzipFileName(dumpOutFn)) {
            os_.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
        }
        os_.push(io::file_sink(dumpOutFn));
        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    void putH(const DumpHP dumpHP) {
        
        assert(isInit_ && ! isStarted_);
        os_ << *dumpHP;
    }
    void putB(const DumpBP dumpBP){

        assert(isInit_ && isStarted_);
        os_ << *dumpBP;
    }
    void start() {

        assert(isInit_);
        isStarted_ = true;
    }
    void stop() {

        assert(isInit_ && isStarted_);
        os_.reset();
    }
    void pause() {}
    void resume() {}
};

/**
 * @brief Interface of digest output manager.
 *
 * init() -> putH() -> start()
 * -> putB() ... -> pause() -> resume()
 * -> putB() ... -> pause() -> resume()
 * ...
 * -> putB() ... -> stop().
 */
class DigestOutManager
{
public:
    /**
     * Constructor.
     */
    DigestOutManager() {}
    /**
     * Destructor.
     */
    virtual ~DigestOutManager() {}
    /**
     * Initializer.
     *
     * @param digestOutFn file name of digest output.
     */
    virtual void init(const std::string& digestOutFn) = 0;
    /**
     * @return True if initialization has finished.
     */
    virtual bool isInit() const = 0;
    /**
     * Put header data.
     */
    virtual void putH(const DigestHP digestHP) = 0;
    /**
     * Put digest data.
     */
    virtual void putB(const DigestBP digestBP) = 0;
    /**
     * Start the manager.
     */ 
    virtual void start() = 0;
    /**
     * Stop the manager.
     */
    virtual void stop() = 0;
    /**
     * Pause related thread if exists to call fork().
     */
    virtual void pause() = 0;
    /**
     * Resume related thread if called after pause().
     */
    virtual void resume() = 0;
};

/**
 * @brief Multi-threaded implementation of DigestOutManager.
 */
class ParallelDigestOutManager
    : virtual public DigestOutManager
{
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_ostream os_;
    DigestWriterP writerP_;
public:
    ParallelDigestOutManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& digestOutFn) {

        WRITE_LOG1("ParallelDigestOutManager::init() called.\n");
        if (isGzipFileName(digestOutFn)) {
            os_.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
        }
        os_.push(io::file_sink(digestOutFn));

        writerP_ = DigestWriterP(new DigestWriter(os_));
        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    void putH(const DigestHP digestHP) {

        assert(isInit_ && ! isStarted_);
        os_ << *digestHP;
    }
    void putB(const DigestBP digestBP) {

        assert(isInit_ && isStarted_);
        assert(writerP_.get() != NULL);
        if (! writerP_->put(digestBP)) {
            throw ExceptionStack("writerP_->put() failed.",
                                 __FILE__, __LINE__);
        }
    }
    void start() {

        WRITE_LOG1("ParallelDigestOutManager::start() called.\n");
        assert(isInit_ && writerP_.get() != NULL);
        writerP_->start();
        isStarted_ = true;
    }
    void stop() {

        WRITE_LOG1("ParallelDigestOutManager::stop() called.\n");
        assert(isInit_ && isStarted_ && writerP_.get() != NULL);
        writerP_->stop(); os_.reset();
    }
    void pause() {

        WRITE_LOG1("ParallelDigestOutManager::pause() called.\n");
        assert(writerP_.get() != NULL);
        writerP_->pause();
    }
    void resume() {

        WRITE_LOG1("ParallelDigestOutManager::resume() called.\n");
        assert(writerP_.get() != NULL);
        writerP_->resume();
    }
};

/**
 * @brief Single-threaded implementation of DigestOutManager.
 */
class SingleDigestOutManager
    : virtual public DigestOutManager
{
private:
    bool isInit_;
    bool isStarted_;
    io::filtering_ostream os_;
public:
    SingleDigestOutManager() : isInit_(false), isStarted_(false) {}
    void init(const std::string& digestOutFn) {

        if (isGzipFileName(digestOutFn)) {
            os_.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
        }
        os_.push(io::file_sink(digestOutFn));

        isInit_ = true;
    }
    bool isInit() const {

        return isInit_;
    }
    void putH(const DigestHP digestHP) {

        assert(isInit_ && ! isStarted_);
        os_ << *digestHP;
    }
    void putB(const DigestBP digestBP) {
        
        assert(isInit_ && isStarted_);
        os_ << *digestBP;
    }
    void start() {

        assert(isInit_);
        isStarted_ = true;
    }
    void stop() {

        assert(isInit_ && isStarted_);
        os_.reset();
    }
    void pause() {}
    void resume() {}
};

#endif /* VMDKBACKUP_ARCHIVE_IO_MANAGER_HPP_ */
