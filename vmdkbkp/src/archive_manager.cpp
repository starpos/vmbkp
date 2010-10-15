/**
 * @file
 * @brief Implementation of ArchiveManager,
 * ArchiveManagerForDump, and MultiArchiveManager.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include "archive_manager.hpp"

/******************************************************************************
 * ArchiveManager members.
 ******************************************************************************/

ArchiveManager::ArchiveManager(const ConfigData& cfg)
    : isOpenDumpIn_(false)
    , isOpenDumpOut_(false)
    , isOpenDigestIn_(false)
    , isOpenDigestOut_(false)
    , isOpenRdiffOut_(false)
    , cfg_(cfg)
{
    /* Initialize archive io managers */
#ifdef VMDKBACKUP_THREAD
    dumpInMgrP_ = boost::shared_ptr<ParallelDumpInManager>
        (new ParallelDumpInManager);
    digestInMgrP_ = boost::shared_ptr<ParallelDigestInManager>
        (new ParallelDigestInManager);
    dumpOutMgrP_ = boost::shared_ptr<ParallelDumpOutManager>
        (new ParallelDumpOutManager);
    digestOutMgrP_ = boost::shared_ptr<ParallelDigestOutManager>
        (new ParallelDigestOutManager);
    rdiffOutMgrP_ = boost::shared_ptr<ParallelDumpOutManager>
        (new ParallelDumpOutManager);
#else
    dumpInMgrP_ = boost::shared_ptr<SingleDumpInManager>
        (new SingleDumpInManager);
    digestInMgrP_ = boost::shared_ptr<SingleDigestInManager>
        (new SingleDigestInManager);
    dumpOutMgrP_ = boost::shared_ptr<SingleDumpOutManager>
        (new SingleDumpOutManager);
    digestOutMgrP_ = boost::shared_ptr<SingleDigestOutManager>
        (new SingleDigestOutManager);
    rdiffOutMgrP_ = boost::shared_ptr<SingleDumpOutManager>
        (new SingleDumpOutManager);
#endif
    
    /* Open files */
    if (! cfg.dumpInFileName.empty()) {
        isOpenDumpIn_ = true;
        dumpInMgrP_->init(cfg.dumpInFileName);
        dumpInMgrP_->start();
    }
    if (! cfg.digestInFileName.empty()) {
        isOpenDigestIn_ = true;
        digestInMgrP_->init(cfg.digestInFileName);
        digestInMgrP_->start();
    }
    if (! cfg.dumpOutFileName.empty()) {
        isOpenDumpOut_ = true;
        dumpOutMgrP_->init(cfg.dumpOutFileName);
    }
    if (! cfg.digestOutFileName.empty()) {
        isOpenDigestOut_ = true;
        digestOutMgrP_->init(cfg.digestOutFileName);
    }
    if (! cfg.rdiffOutFileName.empty()) {
        isOpenRdiffOut_ = true;
        rdiffOutMgrP_->init(cfg.rdiffOutFileName);
    }
    if (! cfg.bmpInFileName.empty()) {
        changedBlockBitmapIn_.open(cfg.bmpInFileName.c_str());
    }

    /* Check required streams are available
       for the specified command and mode. */
    checkStreams();
}

ArchiveManager::~ArchiveManager()
{
    WRITE_LOG1("ArchiveManager destructor begin\n");

    /* Normally input threads have already finished. */
    if (isOpenDumpIn_) { dumpInMgrP_->stop(); }
    if (isOpenDigestIn_) { digestInMgrP_->stop(); }

    /* Normally output threads must flush data in their queue. */
    if (isOpenDumpOut_) { dumpOutMgrP_->stop(); }
    if (isOpenDigestOut_) { digestOutMgrP_->stop(); }
    if (isOpenRdiffOut_) { rdiffOutMgrP_->stop(); }

    if (changedBlockBitmapIn_.is_open()) { changedBlockBitmapIn_.close(); }
    
    WRITE_LOG1("ArchiveManager destructor end\n");
}

void ArchiveManager::checkStreams()
{
    const bool isChangedBlockBitmapIn = changedBlockBitmapIn_.is_open();

    const bool canDumpFull = isOpenDumpOut_ && isOpenDigestOut_;
    const bool canDumpDiff =
        isOpenDumpIn_ && isOpenDigestIn_ &&
        isOpenDumpOut_ && isOpenDigestOut_ &&
        isOpenRdiffOut_;
    const bool canDumpIncr = 
        isOpenDumpIn_ && isOpenDigestIn_ &&
        isOpenDumpOut_ && isOpenDigestOut_ &&
        isChangedBlockBitmapIn && isOpenRdiffOut_;
    const bool canRestore =
        (cfg_.isUseSan ? isOpenDigestIn_ : true);
    const bool canCheck = isOpenDigestIn_;
    const bool canPrint = isOpenDumpIn_ || isOpenDigestIn_;
    const bool canDigest = isOpenDumpIn_ && isOpenDigestOut_;
    const bool canMerge = isOpenDumpOut_ || isOpenRdiffOut_;
    
    switch (cfg_.cmd) {
    case CMD_DUMP:
        switch (cfg_.mode) {
        case DUMPMODE_FULL:
            MY_CHECK_AND_THROW(canDumpFull,
                               "Some streams are not open for dump full.");
            break;
        case DUMPMODE_DIFF:
            MY_CHECK_AND_THROW(canDumpDiff,
                               "Some streams are not open for dump diff.");
            break;
        case DUMPMODE_INCR:
            MY_CHECK_AND_THROW(canDumpIncr,
                               "Streams are not open for dump incr.");
            break;
        case DUMPMODE_UNKNOWN:
        default:
            MY_THROW_ERROR("DumpMode is invalid.");
        }
        break;
    case CMD_RESTORE:
        MY_CHECK_AND_THROW(canRestore,
                           "Some streams are not open for restore.");
        break;
    case CMD_CHECK:
        MY_CHECK_AND_THROW(canCheck,
                           "Some streams are not open for check.");
        break;
    case CMD_PRINT:
        MY_CHECK_AND_THROW(canPrint,
                           "Some streams are not open for print.");
        break;
    case CMD_DIGEST:
        MY_CHECK_AND_THROW(canDigest,
                           "Some streams are not open for digest.");
        break;
    case CMD_MERGE:
        MY_CHECK_AND_THROW(canMerge,
                           "Some streams are not open for merge.");
        break;
    case CMD_UNKNOWN:
    default:
        MY_THROW_ERROR("BackupCommand is invalid.");
    }
}

void ArchiveManager::readFromDump(VmdkDumpBlock& dumpB)
{
    assert(isOpenDumpIn_);
    MY_CHECK_AND_THROW(! dumpInMgrP_->isEnd(), "End of stream.");
    try {
        DumpBP dumpBP = dumpInMgrP_->getB();
        dumpB.copyDataFrom(*dumpBP);
        
    } catch (ExceptionStack& e) {
        e.add("readFromDump()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::readFromDigest(VmdkDigestBlock& digestB)
{
    assert(isOpenDigestIn_);
    MY_CHECK_AND_THROW(! digestInMgrP_->isEnd(), "End of stream.");
    try {
        DigestBP digestBP = digestInMgrP_->getB();
        digestB.copyDataFrom(*digestBP);
        
    } catch (ExceptionStack& e) {
        e.add("readFromDigest()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::writeToDump(const VmdkDumpBlock& dumpB)
{
    assert(isOpenDumpOut_);
    try {
        DumpBP dumpBP = DumpBP(new VmdkDumpBlock(dumpB.blockSize_));
        dumpBP->copyDataFrom(dumpB);
        dumpOutMgrP_->putB(dumpBP);
        
    } catch (ExceptionStack& e) {
        e.add("writeToDump()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::writeToDigest(const VmdkDigestBlock& digestB)
{
    assert(isOpenDigestOut_);
    try {
        DigestBP digestBP = DigestBP(new VmdkDigestBlock);
        digestBP->copyDataFrom(digestB);
        digestOutMgrP_->putB(digestBP);
        
    } catch (ExceptionStack& e) {
        e.add("writeToDigest()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::writeToRdiff(const VmdkDumpBlock& rdiffB)
{
    assert(isOpenRdiffOut_);
    try {
        DumpBP rdiffBP = DumpBP(new VmdkDumpBlock(rdiffB.blockSize_));
        rdiffBP->copyDataFrom(rdiffB);
        rdiffOutMgrP_->putB(rdiffBP);

    } catch (ExceptionStack& e) {
        e.add("writeToRdiff()", __FILE__, __LINE__); throw;
    }
}

bool ArchiveManager::isDumpInOpen() const
{
    return isOpenDumpIn_;
}

bool ArchiveManager::isDigestInOpen() const
{
    return isOpenDigestIn_;
}

void ArchiveManager::readDumpHeader(
    VmdkDumpHeader& dumpH)
{
    assert(isOpenDumpIn_);
    MY_CHECK_AND_THROW(! dumpInMgrP_->isEnd(), "End of stream.");
    try {
        dumpH.copyDataFrom(*dumpInMgrP_->getH());
        
    } catch (ExceptionStack& e) {
        e.add("readDumpHeader()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::readDigestHeader(
    VmdkDigestHeader& digestH)
{
    assert(isOpenDigestIn_);
    MY_CHECK_AND_THROW(! digestInMgrP_->isEnd(), "End of stream.");
    try {
        digestH.copyDataFrom(*digestInMgrP_->getH());
        
    } catch (ExceptionStack& e) {
        e.add("readDigestHeader()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::writeDumpHeader(const VmdkDumpHeader& dumpH)
{
    assert(isOpenDumpOut_);
    try {
        DumpHP dumpHP = DumpHP(new VmdkDumpHeader);
        dumpHP->copyDataFrom(dumpH);
        dumpOutMgrP_->putH(dumpHP);
        dumpOutMgrP_->start(); /* start writer worker. */
        
    } catch (ExceptionStack& e) {
        e.add("writeDumpHeader()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::writeDigestHeader(const VmdkDigestHeader& digestH)
{
    assert(isOpenDigestOut_);
    try {
        DigestHP digestHP = DigestHP(new VmdkDigestHeader);
        digestHP->copyDataFrom(digestH);
        digestOutMgrP_->putH(digestHP);
        digestOutMgrP_->start(); /* start writer worker. */
        
    } catch (ExceptionStack& e) {
        e.add("writeDigestHeader()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::writeRdiffHeader(const VmdkDumpHeader& rdiffH)
{
    assert(isOpenRdiffOut_);
    try {
        DumpHP rdiffHP = DumpHP(new VmdkDumpHeader);
        rdiffHP->copyDataFrom(rdiffH);
        rdiffOutMgrP_->putH(rdiffHP);
        rdiffOutMgrP_->start(); /* start writer worker. */
        
    } catch (ExceptionStack& e) {
        e.add("writeRdiffHeader()", __FILE__, __LINE__); throw;
    }
}

void ArchiveManager::readChangedBlockBitmap(Bitmap& bmp)
{
    assert(changedBlockBitmapIn_.is_open());
    MY_CHECK_AND_THROW(changedBlockBitmapIn_.peek() != EOF, "End of stream.");
    try {
        changedBlockBitmapIn_ >> bmp;
        
    } catch (ExceptionStack& e) {
        e.add("readChangedBlockBitmap()", __FILE__, __LINE__); throw;
    }
}

bool ArchiveManager::canReadFromDump()
{
    assert(isOpenDumpIn_);
    return (! dumpInMgrP_->isEnd());
}

bool ArchiveManager::canReadFromDigest()
{
    assert(isOpenDigestIn_);
    return (! digestInMgrP_->isEnd());
}

void ArchiveManager::pause()
{
    WRITE_LOG1("ArchiveManager::pause() begin\n");
    if (isOpenDumpIn_) {
        assert(dumpInMgrP_.get() != NULL);
        dumpInMgrP_->pause();
    }
    if (isOpenDigestIn_) {
        assert(digestInMgrP_.get() != NULL);
        digestInMgrP_->pause();
    }
    if (isOpenDumpOut_) {
        assert(dumpOutMgrP_.get() != NULL);
        dumpOutMgrP_->pause();
    }
    if (isOpenDigestOut_) {
        assert(digestOutMgrP_.get() != NULL);
        digestOutMgrP_->pause();
    }
    if (isOpenRdiffOut_) {
        assert(rdiffOutMgrP_.get() != NULL);
        rdiffOutMgrP_->pause();
    }
    WRITE_LOG1("ArchiveManager::pause() ends\n");
}

void ArchiveManager::resume()
{
    WRITE_LOG1("ArchiveManager::resume() begin\n");
    if (isOpenDumpIn_) {
        assert(dumpInMgrP_.get() != NULL);
        dumpInMgrP_->resume();
    }
    if (isOpenDigestIn_) {
        assert(digestInMgrP_.get() != NULL);
        digestInMgrP_->resume();
    }
    if (isOpenDumpOut_) {
        assert(dumpOutMgrP_.get() != NULL);
        dumpOutMgrP_->resume();
    }
    if (isOpenDigestOut_) {
        assert(digestOutMgrP_.get() != NULL);
        digestOutMgrP_->resume();
    }
    if (isOpenRdiffOut_) {
        assert(rdiffOutMgrP_.get() != NULL);
        rdiffOutMgrP_->resume();
    }
    WRITE_LOG1("ArchiveManager::resume() ends\n");
}

/******************************************************************************
 * ArchiveManagerForDump members.
 ******************************************************************************/

void ArchiveManagerForDump::readFromStreams(
    VmdkDumpBlock& dumpB, VmdkDigestBlock& digestB)
{
    assert(cfg_.cmd == CMD_DUMP);

    if (cfg_.mode == DUMPMODE_DIFF || cfg_.mode == DUMPMODE_INCR) {

        try {
            readFromDump(dumpB);
            readFromDigest(digestB);
        } catch (ExceptionStack& e) {
            e.add("readFromStreams()", __FILE__, __LINE__); throw;
        }

        /* Check the input dump and digest.
           It's the same operation as "check" command */
        VmdkDigestBlock digestBCheck;
        digestBCheck.set(dumpB);
        MY_CHECK_AND_THROW(digestBCheck == digestB,
                           "Digest check error.");
    } else {
        assert(cfg_.mode == DUMPMODE_FULL);
        /* do nothing */
    }
}

bool ArchiveManagerForDump::writeToStreams(
    const VmdkDumpBlock& prevDumpB, const VmdkDigestBlock& prevDigestB,
    const VmdkDumpBlock& currDumpB, const VmdkDigestBlock& currDigestB)
{
    bool isChanged = true;
    try {
        /* Write dump and digest */
        assert(cfg_.cmd == CMD_DUMP);
        writeToDump(currDumpB);
        writeToDigest(currDigestB);

        /* Write rdiff */
        if ((cfg_.mode == DUMPMODE_DIFF || cfg_.mode == DUMPMODE_INCR)) {
            if (prevDigestB != currDigestB) {
                /* rdiff must write old block data */
                writeToRdiff(prevDumpB);
            } else {
                isChanged = false;
            }
        }
    } catch (ExceptionStack& e) {
        e.add("writeToStreams()", __FILE__, __LINE__); throw;
    }
    return isChanged;
}

void ArchiveManagerForDump::readHeaders(
    VmdkDumpHeader& dumpH, VmdkDigestHeader& digestH)
{
    assert(cfg_.cmd == CMD_DUMP);

    if (cfg_.mode == DUMPMODE_DIFF || cfg_.mode == DUMPMODE_INCR) {

        try {
            readDumpHeader(dumpH);
            readDigestHeader(digestH);
            
        } catch (ExceptionStack& e) {
            e.add("readHeaders()", __FILE__, __LINE__); throw;
        }
        
        MY_CHECK_AND_THROW(isTheSameSnapshot(dumpH, digestH),
                           "dump and digest are not derived"
                           " from the same vmdk snapshot.");
        MY_CHECK_AND_THROW(dumpH.isFull(),
                           "dump must be a full dump.");
    
    } else {
        assert(cfg_.mode == DUMPMODE_FULL);
    }
}

void ArchiveManagerForDump::readChangedBlockBitmap(Bitmap& bmp)
{
    WRITE_LOG1("readChangedBlockBitmap() called.\n");
    assert(cfg_.cmd == CMD_DUMP);
    
    if (cfg_.mode == DUMPMODE_INCR) {

        try {
            ArchiveManager::readChangedBlockBitmap(bmp);
            
        } catch (ExceptionStack& e) {
            e.add("readChangedBlockBitmap()", __FILE__, __LINE__); throw;
        }
        
    } else {
        assert(cfg_.mode == DUMPMODE_FULL || cfg_.mode == DUMPMODE_DIFF);
    }
}

void ArchiveManagerForDump::setHeaders(
    const VmdkInfo& vmdkInfo,
    const VmdkDumpHeader& prevDumpH,
    __attribute__((unused)) const VmdkDigestHeader& prevDigestH,
    VmdkDumpHeader& currDumpH,
    VmdkDigestHeader& currDigestH,
    VmdkDumpHeader& rdiffH)
{
    time_t now = ::time(0);

    currDumpH.initialize(vmdkInfo.nBlocks,
                        cfg_.blocksize,
                        vmdkInfo.adapterType);
    currDumpH.setTimeStamp(now);

    currDigestH.initialize(vmdkInfo.nBlocks, cfg_.blocksize);
    currDigestH.setTimeStamp(now);

    assert(currDumpH.getDiskSize() == currDigestH.getDiskSize());
    assert(currDumpH.getBlockSize() == currDigestH.getBlockSize());

    if (cfg_.mode == DUMPMODE_DIFF || cfg_.mode == DUMPMODE_INCR) {
        assert (isTheSameSnapshot(prevDumpH, prevDigestH));
        currDumpH.setUUID(prevDumpH.getUUID());
        /* The timestamp of rdiff is set to the previous dump's one. */
        rdiffH.copyDataFrom(prevDumpH);
        rdiffH.setFull(false);
    } else {
        currDumpH.setUUID();
    }
    currDigestH.setUUID(currDumpH.getUUID());
}

void ArchiveManagerForDump::writeHeaders(
    const VmdkDumpHeader& dumpH,
    const VmdkDigestHeader& digestH,
    const VmdkDumpHeader& rdiffH)
{
    WRITE_LOG1("writeHeaders() called.\n");

    try {
        assert(cfg_.cmd == CMD_DUMP);
    
        writeDumpHeader(dumpH);
        writeDigestHeader(digestH);
    
        if (cfg_.mode == DUMPMODE_DIFF || cfg_.mode == DUMPMODE_INCR) {
            writeRdiffHeader(rdiffH);
        }
    } catch (ExceptionStack& e) {
        e.add("writeHeaders()", __FILE__, __LINE__); throw;
    }
}

/******************************************************************************
 * MultiArchiveManager members.
 ******************************************************************************/

MultiArchiveManager::MultiArchiveManager(
    const std::vector<std::string>& archiveList)
    : archiveList_(archiveList)
    , nArchives_(archiveList.size())
    , dumpInMgrPs_(nArchives_)
    , dumpHPs_(nArchives_)
    , dumpBPs_(nArchives_)
    , dumpEofs_(nArchives_, false)
    , offset_(0)
{
    MY_CHECK_AND_THROW(nArchives_ > 0,
                       "MultiArchiveManager(): archiveList size is 0.\n");
    
    /* Open archives and read header data. */
    for (size_t i = 0; i < nArchives_; i ++) {

#ifdef VMDKBACKUP_THREAD
        dumpInMgrPs_[i] = boost::shared_ptr<ParallelDumpInManager>
            (new ParallelDumpInManager);
#else
        dumpInMgrPs_[i] = boost::shared_ptr<SingleDumpInManager>
            (new SingleDumpInManager);
#endif
        assert(dumpInMgrPs_[i].get() != NULL);
        
        dumpInMgrPs_[i]->init(archiveList_[i]);
        dumpInMgrPs_[i]->start();

        dumpHPs_[i] = dumpInMgrPs_[i]->getH();
        MY_CHECK_AND_THROW(dumpHPs_[i].get() != NULL,
                           "MultiArchiveManager(): dumpHP is NULL.\n");

        if (i == 0) {
            diskSize_ = dumpHPs_[i]->getDiskSize();
            blockSize_ = dumpHPs_[i]->getBlockSize();
            uuid_ = dumpHPs_[i]->getUUID();
        } else {
            MY_CHECK_AND_THROW
                (dumpHPs_[i]->getDiskSize() == diskSize_,
                 "MultiArchiveManager(): disksize is different.\n");
            MY_CHECK_AND_THROW
                (dumpHPs_[i]->getBlockSize() == blockSize_,
                 "MultiArchiveManager(): blocksize is different.\n");
            MY_CHECK_AND_THROW
                (dumpHPs_[i]->getUUID() == uuid_,
                 "MultiArchiveManager(): uuid is different.\n");
            if (! dumpHPs_[i-1]->isFull() && ! dumpHPs_[i]->isFull()) {
                MY_CHECK_AND_THROW
                    (dumpHPs_[i-1]->getTimeStamp() > dumpHPs_[i]->getTimeStamp(),
                     "MultiArchiveManager(): timestamp order is not correct.\n");
            }
        }

        /* Read first block data. */
        if (! dumpInMgrPs_[i]->isEnd()) {
            
            try {
                dumpBPs_[i] = dumpInMgrPs_[i]->getB();
            } catch (ExceptionStack& e) {
                e.add("getB() failed.", __FILE__, __LINE__);
                throw;
            }
            
        } else {
            dumpEofs_[i] = true;
        }
    }

    /* Generate dumpH of the virtual archive. */
    dumpH_.copyDataFrom(*dumpHPs_.back());
    dumpH_.setFull(dumpHPs_.front()->isFull());
    dumpH_.setTimeStamp(dumpHPs_.back()->getTimeStamp());
}

MultiArchiveManager::~MultiArchiveManager()
{
    WRITE_LOG1("~MultiArchiveManager() begin.\n");
    
    /* Normally input threads have already finished.
       This is for emergent exit. */
    for (size_t i = 0; i < nArchives_; i ++) {

        assert(dumpInMgrPs_[i].get() != NULL);
        dumpInMgrPs_[i]->stop();
    }
    WRITE_LOG1("~MultiArchiveManager() end.\n");
}

uint64 MultiArchiveManager::getOffset() const
{
    return offset_;
}

uint64 MultiArchiveManager::getBlockSize() const
{
    return blockSize_;
}

uint64 MultiArchiveManager::getDiskSize() const
{
    return diskSize_;
}

bool MultiArchiveManager::isEOF() const
{
    assert(offset_ <= diskSize_);
    return offset_ == diskSize_;
}

bool MultiArchiveManager::readBlock(VmdkDumpBlock& dumpB)
{
    /* Check EOF. */
    if (isEOF()) { return false; }
    
    /* Get the latest block of offset_. */
    bool isExist = false;
    DumpBP dumpBP;
    for (size_t i = 0; i < nArchives_; i ++) {

        assert(! dumpEofs_[i] && dumpBPs_[i].get() != NULL);
        if (! dumpEofs_[i] && dumpBPs_[i]->getOffset() == offset_) {

            isExist = true;
            dumpBP = dumpBPs_[i];

            /* Read next block data. */
            if (! dumpInMgrPs_[i]->isEnd()) {

                try {
                    dumpBPs_[i] = dumpInMgrPs_[i]->getB();
                } catch (ExceptionStack& e) {
                    e.add("getB() failed.", __FILE__, __LINE__);
                    throw;
                }
            } else {
                dumpEofs_[i] = true;
            }
        }
    }
    assert (isExist && dumpBP.get() != NULL);
    if (isExist) {
        dumpB.copyDataFrom(*dumpBP);
    }
    offset_ ++;
    return isExist;
}

void MultiArchiveManager::getDumpHeader(VmdkDumpHeader& dumpH)
{
    dumpH.copyDataFrom(dumpH_);
}

void MultiArchiveManager::pause()
{
    WRITE_LOG1("ArchiveManager::pause() begin.\n");
    assert(nArchives_ == dumpInMgrPs_.size());
    for (size_t i = 0; i < nArchives_; i ++) {

        assert(dumpInMgrPs_[i].get() != NULL);
        dumpInMgrPs_[i]->pause();
    }
    WRITE_LOG1("ArchiveManager::pause() end.\n");
}

void MultiArchiveManager::resume()
{
    WRITE_LOG1("ArchiveManager::resume() begin.\n");
    assert(nArchives_ == dumpInMgrPs_.size());
    for (size_t i = 0; i < nArchives_; i ++) {

        assert(dumpInMgrPs_[i].get() != NULL);
        dumpInMgrPs_[i]->resume();
    }
    WRITE_LOG1("ArchiveManager::resume() end.\n");
}

/* end of file */
