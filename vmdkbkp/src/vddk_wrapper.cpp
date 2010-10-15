/**
 * @file
 * @brief VddkWorker and VddkController implementation.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */

#include <string>

#include "vddk_wrapper.hpp"

/******************************************************************************
 * Local functions.
 ******************************************************************************/

/**
 * These are for child process.
 */
static VddkWorker *vddkWorkerLocalPtr = NULL;

void exitProcess()
{
    if (vddkWorkerLocalPtr != NULL) {
        vddkWorkerLocalPtr->exitVddk();
    }
    ::exit(1);
}

void signalTermHandler(__attribute__((unused)) int signo)
{
    assert(signo == SIGTERM);
    exitProcess();
}

void signalIntHandler(__attribute__((unused)) int signo)
{
    assert(signo == SIGINT);
    exitProcess();
}

/******************************************************************************
 * VddkWorker members.
 ******************************************************************************/
      
VddkWorker::VddkWorker(
    const ConfigData& cfg, bool isReadOnly, bool isSAN)
    : ForkManager()
    , cfg_(cfg)
    , isReadOnly_(isReadOnly)
    , isSAN_(isSAN)
{}

VddkWorker::~VddkWorker()
{
    if (! isChild_) {
        exitChild();
    }
}

StreamSocketPtr VddkWorker::getSock()
{
    if (sockP_.get() == NULL) {
        sockP_ = StreamSocketPtr
            (new StreamSocket(getIstream(), getOstream()));
    }
    assert(sockP_.get() != NULL);
    return sockP_;
}

const ConfigData& VddkWorker::getConfig() const
{
    return cfg_;
}

int VddkWorker::run() {

    WRITE_LOG1("VddkWorker::run() begin\n");
    
    /* For signal from parent. */
    vddkWorkerLocalPtr = this;
    ::signal(SIGTERM, SIG_IGN);
    ::signal(SIGINT, SIG_IGN);
    
    int ret = 0;
    try {
        vddkMgrPtr_ = boost::shared_ptr<VddkManager>
            (new VddkManager(cfg_, isReadOnly_, isSAN_));
        vmdkMgrPtr_ = boost::shared_ptr<VmdkManager>
            (new VmdkManager(*vddkMgrPtr_));

    } catch (const VixException& e) {

        WRITE_LOG0("VddkManager or VmdkManager constructor failed.\n");
        e.writeLog();
        exitVddk();
        ret = 2;
        return ret;
    }

    WRITE_LOG1("vddkMgrPtr_ and vmdkMgrPtr_ have ben initialized.\n");
    
    /* This handler will be called in an emergency. */
    if (::signal(SIGTERM, signalTermHandler) == SIG_ERR ||
        ::signal(SIGINT, signalIntHandler) == SIG_ERR) {
        WRITE_LOG0("signal system call failed.");
    }

    /* This set sockP_. */
    {
        StreamSocketPtr sockP = getSock();
        assert(sockP.get() != NULL);
    }
    
    while (true) {

        std::string cmd;
        try {
            cmd = sockP_->recvMsg();
            if (cmd == "EXIT") { sockP_->sendMsg("OK"); break; }
            
        } catch (const std::string& e) {
            
            WRITE_LOG0("Exception %s\n", e.c_str());
            ret = 3;
            break;
        }
        
        try {
            ret = dispatch(cmd);

        } catch (const VixException& e) {
            e.writeLog();
            ret = 4; break;
        } catch (const ExceptionStack& e) {
            WRITE_LOG0("%s\n", e.sprint().c_str());
            ret = 5; break;
        } catch (const MyException& e) {
            WRITE_LOG0("%s\n", e.sprint().c_str());
            ret = 6; break;
        } catch (const std::string& e) {
            WRITE_LOG0("%s\n", e.c_str());
            ret = 7; break;
        }
            
        if (ret != 0) {
            WRITE_LOG0("dispatch(%s) failed with %d return value.\n",
                       cmd.c_str(), ret);
        }
    }

    exitVddk();
    
    ::signal(SIGTERM, SIG_DFL);
    ::signal(SIGINT, SIG_DFL);
    
    WRITE_LOG1("VddkWorker::run() ends\n");
    return ret;
}

int VddkWorker::dispatch(const std::string& cmd)
{
    if (cmd == "open") {
        return open();
    } else if (cmd == "close") {
        return close();
    } else if (cmd == "createVmdkFile") {
        return createVmdkFile();
    } else if (cmd == "shrinkVmdk") {
        return shrinkVmdk();
    } else if (cmd == "getTransportMode") {
        return getTransportMode();
    } else if (cmd == "readVmdkInfo") {
        return readVmdkInfo();
    } else if (cmd == "readMetadata") {
        return readMetadata();
    } else if (cmd == "writeMetadata") {
        return writeMetadata();
    } else if (cmd == "readBlock") {
        return readBlock();
    } else if (cmd == "writeBlock") {
        return writeBlock();
    } else {
        WRITE_LOG0("VddkWorker::dispatch() unknown command '%s'\n",
                   cmd.c_str());
        return -1;
    }
}

int VddkWorker::open()
{
    WRITE_LOG0("VddkWorker::open() begin.\n");
    assert(sockP_.get() != NULL);
    try {
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->open();
        sockP_->sendMsg("OK");
        WRITE_LOG0("VddkWorker::open() end.\n");
        return 0;
        
    } catch (const VixException& e) {
        e.writeLog();
    }
    sockP_->sendMsg("EXCEPTION");
    WRITE_LOG0("VddkWorker::open() failed.\n");
    return -1;
}

int VddkWorker::close()
{
    WRITE_LOG0("VddkWorker::close() begin.\n");
    assert(sockP_.get() != NULL);
    assert(vmdkMgrPtr_.get() != NULL);
    vmdkMgrPtr_->close();
    sockP_->sendMsg("OK");

    WRITE_LOG0("VddkWorker::close() end.\n");
    return 0;
}

int VddkWorker::createVmdkFile()
{
    WRITE_LOG1("VddkWorker::createVmdkFile() begin.\n");
    assert(sockP_.get() != NULL);
    try {
        VmdkDumpHeader dumpH;
        sockP_->getIs() >> dumpH;
        if (vddkMgrPtr_.get() != NULL) {
            vddkMgrPtr_->createVmdkFile(dumpH);
            sockP_->sendMsg("OK");
        }
        WRITE_LOG1("VddkWorker::createVmdkFile() end.\n");
        return 0;
        
    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    } catch (const MyException& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    WRITE_LOG1("VddkWorker::createVmdkFile() failed.\n");
    return -1;
}

int VddkWorker::shrinkVmdk()
{
    WRITE_LOG1("VddkWorker::shrinkVmdkFile() begin.\n");
    assert(sockP_.get() != NULL);
    try {
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->shrinkVmdk();
        sockP_->sendMsg("OK");
        WRITE_LOG1("VddkWorker::shrinkVmdkFile() end.\n");
        return 0;
        
    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    WRITE_LOG1("VddkWorker::shrinkVmdkFile() failed.\n");
    return -1;
}

int VddkWorker::getTransportMode()
{
    WRITE_LOG1("VddkWorker::getTransportMode() begin.\n");
    assert(sockP_.get() != NULL);
    assert(vmdkMgrPtr_.get() != NULL);
    std::string ret = vmdkMgrPtr_->getTransportMode();
    sockP_->sendMsg(ret);
    WRITE_LOG1("VddkWorker::getTransportMode() end.\n");
    return 0;
}

int VddkWorker::readVmdkInfo()
{
    WRITE_LOG1("VddkWorker::readVmdkInfo() begin.\n");
    assert(sockP_.get() != NULL);
    VmdkInfo vmdkInfo;
    try {
        std::ostream& os = sockP_->getOs();
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->readVmdkInfo(vmdkInfo);
        sockP_->sendMsg("OK");
        os << vmdkInfo; os.flush();
        WRITE_LOG1("VddkWorker::readVmdkInfo() end.\n");
        return 0;
        
    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    WRITE_LOG1("VddkWorker::readVmdkInfo() failed.\n");
    return -1;
}

int VddkWorker::readMetadata()
{
    WRITE_LOG1("VddkWorker::readMetadata() begin.\n");
    assert(sockP_.get() != NULL);
    try {
        std::ostream& os = sockP_->getOs();
        StringMap metadata;
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->readMetadata(metadata);
        sockP_->sendMsg("OK");
        os << metadata; os.flush();
        WRITE_LOG1("VddkWorker::readMetadata() end.\n");
        return 0;

    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    WRITE_LOG1("VddkWorker::readMetadata() failed.\n");
    return -1;
}

int VddkWorker::writeMetadata()
{
    WRITE_LOG1("VddkWorker::writeMetadata() begin.\n");
    assert(sockP_.get() != NULL);
    try {
        StringMap metadata;
        sockP_->getIs() >> metadata;
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->writeMetadata(metadata);
        sockP_->sendMsg("OK");
        WRITE_LOG1("VddkWorker::writeMetadata() end.\n");
        return 0;
        
    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    WRITE_LOG1("VddkWorker::writeMetadata() failed.\n");
    return -1;
}

int VddkWorker::readBlock()
{
    assert(sockP_.get() != NULL);
    try {
        uint64 blockOffset;
        uint8 buf[cfg_.blocksize];

        getAsString(blockOffset, sockP_->getIs());
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->readBlock(blockOffset, buf);
        sockP_->sendMsg("OK");
        std::ostream& os = sockP_->getOs();
        os.write(reinterpret_cast<const char*>(buf), cfg_.blocksize);
        os.flush();
        return 0;
        
    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    return -1;
}

int VddkWorker::writeBlock()
{
    assert(sockP_.get() != NULL);
    try {
        uint64 blockOffset;
        uint8 buf[cfg_.blocksize];
        
        getAsString(blockOffset, sockP_->getIs());
        sockP_->getIs().read(reinterpret_cast<char*>(buf), cfg_.blocksize);
        assert(vmdkMgrPtr_.get() != NULL);
        vmdkMgrPtr_->writeBlock(blockOffset, buf);
        sockP_->sendMsg("OK");
        return 0;

    } catch (const VixException& e) {
        e.writeLog();
    } catch (const ExceptionStack& e) {
        WRITE_LOG0("%s", e.sprint().c_str());
    }
    sockP_->sendMsg("EXCEPTION");
    return -1;
}

int VddkWorker::exitChild()
{
    if (! isChild_) {
        assert(sockP_.get() != NULL);
        WRITE_LOG1("VddkWorker::exitChild() begin.\n");
        sockP_->sendMsg("EXIT");
        try {
            std::string res = sockP_->recvMsg();
            if (res == "OK") {
                wait();
            } else {
                throw std::string("exitChild: response is not OK.");
            }
        } catch (const std::string& e) {
            WRITE_LOG0("%s", e.c_str());
        }
        WRITE_LOG1("VddkWorker::exitChild() end.\n");
        return 0;
    }        
    WRITE_LOG1("VddkWorker::exitChild() failed.\n");
    return -1;
}

void VddkWorker::exitVddk()
{
    WRITE_LOG1("VddkWorker::exitVddk() begin.\n");
    vmdkMgrPtr_.reset();
    vddkMgrPtr_.reset();
    WRITE_LOG1("VddkWorker::exitVddk() end.\n");
}

/******************************************************************************
 * VddkController members.
 ******************************************************************************/

VddkController::VddkController(const ConfigData& cfg, bool isReadOnly, bool isSAN)
    : vddkWorkerPtr_(boost::shared_ptr<VddkWorker>(new VddkWorker(cfg, isReadOnly, isSAN)))
    , cfg_(vddkWorkerPtr_->getConfig())
{}

VddkController::~VddkController()
{
    stop();
}

void VddkController::kill(int signum)
{
    WRITE_LOG1("VddkController::kill() begin.\n");
    if (vddkWorkerPtr_.get() != NULL) {
        vddkWorkerPtr_->kill(signum);
    }
    WRITE_LOG1("VddkController::kill() end.\n");
}

void VddkController::start()
{
    WRITE_LOG1("VddkController::start() begin.\n");

    assert(vddkWorkerPtr_.get() != NULL);
    bool started = vddkWorkerPtr_->start();
    MY_CHECK_AND_THROW(started, "VddkWorker start failed.");
    
    assert(sockP_.get() == NULL);
    sockP_ = vddkWorkerPtr_->getSock();
    assert(sockP_.get() != NULL);
    
    WRITE_LOG1("VddkController::start() end.\n");
}

void VddkController::stop()
{
    WRITE_LOG1("VddkController::stop() start.\n");
    if (vddkWorkerPtr_.get() != NULL) {
        /* This call internally killing child process. */
        vddkWorkerPtr_.reset();
    }
    sockP_.reset();
    
    WRITE_LOG1("VddkController::stop() end.\n");
}

bool VddkController::reset(bool isReadOnly, bool isSAN)
{
    WRITE_LOG1("VddkController::reset() begin.\n");

    stop();
    
    vddkWorkerPtr_ = boost::shared_ptr<VddkWorker>
        (new VddkWorker(cfg_, isReadOnly, isSAN));

    try {
        start();

    } catch (const MyException& e) {
        WRITE_LOG0("VddkController::reset() start failed '%s'.\n",
                   e.sprint().c_str());
        return false;
    }
    assert(sockP_.get() != NULL);
    
    WRITE_LOG1("VddkController::reset() end.\n");
    return true;
}

void VddkController::open()
{
    WRITE_LOG1("VddkController::open() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("open");
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK", "open() failed.");
    WRITE_LOG1("VddkController::open() end.\n");
}

void VddkController::close()
{
    WRITE_LOG1("VddkController::close() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("close");
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK", "close() failed.");
    WRITE_LOG1("VddkController::close() end.\n");
}

void VddkController::createVmdkFile(const VmdkDumpHeader& dumpH)
{
    WRITE_LOG1("VddkController::createVmdkFile() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("createVmdkFile");
    std::ostream& os = sockP_->getOs();
    os << dumpH; os.flush();
    
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "createVmdkFile() failed.");
    WRITE_LOG1("VddkController::createVmdkFile() end.\n");
}

void VddkController::shrinkVmdk()
{
    WRITE_LOG1("VddkController::shrinkVmdk() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("shrinkVmdk");
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "shrinkVmdk() failed.");
    WRITE_LOG1("VddkController::shrinkVmdk() end.\n");
}

std::string VddkController::getTransportMode() const
{
    WRITE_LOG1("VddkController::getTransportMode() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("getTransportMode");
    WRITE_LOG1("VddkController::getTransportMode() end.\n");
    return sockP_->recvMsg();
}

void VddkController::readVmdkInfo(VmdkInfo& vmdkInfo)
{
    WRITE_LOG1("VddkController::readVmdkInfo() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("readVmdkInfo");
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "readVmdkInfo() failed.");
    sockP_->getIs() >> vmdkInfo;
    WRITE_LOG1("VddkController::readVmdkInfo() end.\n");
}

void VddkController::readMetadata(StringMap& metadata)
{
    WRITE_LOG1("VddkController::readMetadata() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("readMetadata");
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "readMetadata() failed.");
    sockP_->getIs() >> metadata;
    WRITE_LOG1("VddkController::readMetadata() end.\n");
}

void VddkController::writeMetadata(const StringMap& metadata)
{
    WRITE_LOG1("VddkController::writeMetadata() begin.\n");
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("writeMetadata");
    std::ostream& os = sockP_->getOs();
    os << metadata; os.flush();

    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "writeMetadata() failed.");
    WRITE_LOG1("VddkController::writeMetadata() end.\n");
}

void VddkController::readBlock(
    const uint64 blockOffset, uint8* buf)
{
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("readBlock");
    std::ostream& os = sockP_->getOs();
    putAsString(os, blockOffset); os.flush();
    
    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "readBlock() failed.");

    sockP_->getIs().read(reinterpret_cast<char*>(buf), cfg_.blocksize);
}

void VddkController::writeBlock(
    const uint64 blockOffset, const uint8* buf)
{
    assert(sockP_.get() != NULL);
    sockP_->sendMsg("writeBlock");
    std::ostream& os = sockP_->getOs();
    putAsString(os, blockOffset);
    os.write(reinterpret_cast<const char*>(buf), cfg_.blocksize);
    os.flush();

    std::string res = sockP_->recvMsg();
    MY_CHECK_AND_THROW(res == "OK",
                       "writeBlock() failed.");
}

/* end of file */
