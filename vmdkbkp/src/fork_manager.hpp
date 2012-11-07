/**
 * @file
 * @brief Definition of ForkManager.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_FORK_MANAGER_HPP_
#define VMDKBACKUP_FORK_MANAGER_HPP_

#include <iostream>
#include <string>
#include <sstream>

#include <stdio.h>
#include <unistd.h>
#include <signal.h>
#include <assert.h>
#include <stdlib.h>
#include <sys/wait.h>

#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/device/file_descriptor.hpp>

#include "macro.hpp"

namespace io = boost::iostreams;

/**
 * @brief Manager forked child process and pipes from/to parent.
 *
 * Child process will call run() then end.
 * Override run() method for specific work in parallel.
 * You must call start() before calling any other method.
 */
class ForkManager
{
protected:
    /**
     * Parent -> child transfer.
     */
    int p2cFd_[2];
    /**
     * Child -> parent transfer.
     */
    int c2pFd_[2];
    /**
     * Alias to file descripters of the pipes.
     */
    int& parentInFd_;
    int& parentOutFd_;
    int& childInFd_;
    int& childOutFd_;
    /**
     * True if the proess is child.
     */
    bool isChild_;
    /**
     * Child process id.
     * 0 in child process.
     */
    pid_t childPid_;
    /**
     *
     */
    pid_t parentPid_;
    /**
     * Input stream. This is automatically set.
     */
    io::filtering_istream is_;
    /**
     * Output stream. This is automatically set.
     */
    io::filtering_ostream os_;
    
public:
    /**
     * Constructor.
     *
     * Causion! The constructor is called by parent process.
     * Child process will be really created inside start() method.
     */
    ForkManager()
        : parentInFd_(c2pFd_[0])
        , parentOutFd_(p2cFd_[1])
        , childInFd_(p2cFd_[0])
        , childOutFd_(c2pFd_[1])
        , isChild_(false) {

        parentInFd_ = -1;
        parentOutFd_ = -1;
        childInFd_ = -1;
        childOutFd_ = -1;
        
        int ret1 = ::pipe(p2cFd_);
        int ret2 = ::pipe(c2pFd_);
        if (ret1 != 0 || ret2 != 0) {
            WRITE_LOG0("ForkManager() pipe() call failed.");
            ::exit(1);
        }

        WRITE_LOG1("ForkManager::ForkManager(): "
                   "p_in %d p_out %d c_in %d c_out %d\n",
                   parentInFd_, parentOutFd_, childInFd_, childOutFd_);

        ::sigignore(SIGCHLD);
    }
    /**
     * Destructor.
     */
    virtual ~ForkManager() {
        
        /* Child call exit before
           This object is destroyed. */
        if (! isChild_) {
            finalizeParent();
        }
    }
    /**
     * Start child process.
     * Never return in the child process.
     *
     * @return False if failed to communicate with child process.
     */
    bool start() {

        childPid_ = ::fork();
        if (childPid_ == 0) {

            /* Child */
            isChild_ = true;
            parentPid_ = ::getppid();
            initializeChild();

            int ret = -1;
            if (isValidPipe()) { ret = run(); }

            finalizeChild();
            ::exit(ret);

        } else {

            /* Parent */
            isChild_ = false;
            initializeParent();
            
            return isValidPipe();
        }
    }
    /**
     * Get input stream.
     * Different stream descripter will be returned for parent/child.
     * Parent process must call this after calling start().
     * Child process must call this inside run().
     */
    std::istream& getIstream() {

        return is_;
    }
    /**
     * Get output stream.
     * Different stream descripter will be returned for parent/child.
     *
     * Parent process must call this after calling start().
     * Child process must call this inside run().
     */
    std::ostream& getOstream() {

        return os_;
    }
    /**
     * Wait child process ends.
     * Only parent call this.
     */
    void wait() {

        if (! isChild_) {
            int status;
            ::waitpid(childPid_, &status, 0);
        }
    }
    /**
     * Send signal to child process.
     */
    void kill(int signum) {

        if (! isChild_) {
            ::kill(childPid_, signum);
        }
    }
    
protected:
    /**
     * Get command from parent
     * and execute it.
     * Do not throw any exception.
     */
    virtual int run() = 0;
private:
    /**
     * Initialize parent process.
     */
    void initializeParent() {

        if (isChild_) { return; }
        WRITE_LOG1("ForkManager::initializeParent() called.\n");
        ::close(childInFd_);
        ::close(childOutFd_);
        is_.push(io::file_descriptor_source(parentInFd_, io::never_close_handle));
        os_.push(io::file_descriptor_sink(parentOutFd_, io::never_close_handle));
    }
    /**
     * Initialize child process.
     */
    void initializeChild() {

        if (! isChild_) { return; }
        WRITE_LOG1("ForkManager::initializeChild() called.\n");
        ::close(parentInFd_);
        ::close(parentOutFd_);
        is_.push(io::file_descriptor_source(childInFd_, io::never_close_handle));
        os_.push(io::file_descriptor_sink(childOutFd_, io::never_close_handle));
    }
    /**
     * Finalize parent process.
     */
    void finalizeParent() {

        if (isChild_) { return; }
        WRITE_LOG1("ForkManager::finalizeParent() called.\n");
        is_.reset();
        os_.reset();
        ::close(parentInFd_);
        ::close(parentOutFd_);
    }
    /**
     * Finalize child process.
     */
    void finalizeChild() {

        if (! isChild_) { return; }
        WRITE_LOG1("ForkManager::finalizeChild() called.\n");
        is_.reset();
        os_.reset();
        ::close(childInFd_);
        ::close(childOutFd_);
    }
    /**
     * Check parent-child communication pipe.
     * Both parent and child must call this function at the same timing.
     *
     * You must call this function after
     * calling initializeParent()/initializeChild().
     *
     * <pre>
     * Protocol
     * p: "CHECK"
     * c: "OK"
     * p: "ACK"
     * </pre>
     *
     * @return False if the test communication failed.
     */
    bool isValidPipe() {

        if (isChild_) {

            std::string check; std::getline(is_, check);
            WRITE_LOG1("child: recv %s\n", check.c_str());
            if (is_.fail() || check != "CHECK") { return false; }

            os_ << "OK" << std::endl;
            if (os_.fail()) { return false; }

            std::string ack; std::getline(is_, ack);
            WRITE_LOG1("child: recv %s\n", ack.c_str());
            if (is_.fail() || ack != "ACK") { return false; }

            return true;

        } else {

            os_ << "CHECK" << std::endl;
            if (os_.fail()) { return false; }

            std::string ok; std::getline(is_, ok);
            WRITE_LOG1("parent: recv %s\n", ok.c_str());
            if (is_.fail() || ok != "OK") { return false; }
            
            os_ << "ACK" << std::endl;
            if (os_.fail()) { return false; }
            
            return true;
        }
    }
};

#endif /* VMDKBACKUP_FORK_MANAGER_HPP_ */
