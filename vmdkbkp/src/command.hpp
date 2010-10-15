/**
 * @file
 * @brief Parse command-line and execute the command.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_COMMAND_HPP_
#define VMDKBACKUP_COMMAND_HPP_

#include "header.hpp"
#include "archive_manager.hpp"
#include "vddk_wrapper.hpp"

/**
 * @brief Parse command-line and execute the command.
 */
class Command
{
private:
    /**
     * All config data which are shared with all member function.
     */
    ConfigData cfg_;

public:
    /**
     * Constructor.
     */
    Command() {}
    /**
     * Parse commandline options and set global configuration setting
     * to the global struct valiable 'cfg_'.
     *
     * @param argc argc of main().
     * @param argv argv of main().
     * @return true in success, or false.
     */
    bool parseCommandlineOptions(int argc, char * const argv[]);
    /**
     * Show help messsages.
     */
    void showHelp() const;
    /**
     * Run specified command.
     */
    bool run();
    /**
     * Dump from remote/local vmdk file.
     * Full/Diff/Incremental modes are supported.
     *
     * Input/Output:
     *   --dumpin:    input dump (must be full image).
     *   --dumpout:   output dump (up-to-date full image).
     *   --digestin:  input digest.
     *   --digestout: output digest (up-to-date).
     *   --bmpin:     input changedbmp changed block bitmap.
     *   --rdiffout:  output rdiff.
     *                (reverse difference between input dump
     *                 and output dump)
     ***********************************************************
     * Full backup:
     *   read blocks from vmdk file
     *   write to dumpout and digestout.
     *
     * Diff/Incremental backup:
     *   read from dumpin and digestin (must), 
     *             bmpin (incremental only).
     *   write to dumpout, digestout, rdiffout (must).
     ***********************************************************
     *
     * TODO
     * 1. make the processing of the three-streams in parallel.
     *
     * @exception Throws VixException, ExceptionStack.
     */
    void doDumpNoFork();/* obsolute */
    void doDumpFork(); 
    /**
     * Do dump test read vmdk only.
     */
    void doDumpTest();
    /**
     * Restoring dump data to remote/local vmdk file.
     *
     * @exception Throws VixException, ExceptionStack.
     */
    void doRestore();
    /**
     * Merge rdiff vmdkdump to a full vmdkdump file.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void doMerge();
    /**
     * Check all blocks in the dump file with the specified digest file
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void doCheck();
    /**
     * Make digest file from full dump file.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void doDigest();
    /**
     * Print dump file or digest file for human readability.
     *
     * @exception Throw MyException, ExceptionStack.
     */
    void doPrint();
    /**
     * Make rdiff vmdkdump from two full vmdkdump files.
     *
     * Not implemented yet.
     */
    void doRdiff();
    /**
     * Lock the lock file to execute exclusive operation.
     */
    void doLock();

private:
    /**
     * Restore remote vmdk via NBD or local vmdk.
     *
     * Just write required blocks via NBD for remote vmdk.
     */
    void doRestoreNBD(MultiArchiveManager& mArcMgr);
    /**
     * Restore remote vmdk via SAN.
     *
     * 1. allocate required blocks by writing zero block
     *    with NBD connection.
     *    (Allocation speed for thin vmdk via SAN is too slow!)
     * 2. write required blocks with SAN connection.
     */
    void doRestoreSAN(MultiArchiveManager& mArcMgr);
    /**
     * Allocate blocks by writing zero block
     * using digest data (allocate non-zero blocks only).
     *
     * @param vddkCtrl vddk controller (NBD).
     *        Target vmdk must be open.
     * @param arcMgr archive manager for digestIn only.
     */
    void allocateNonZeroBlock(VddkController& vddkCtrl,
                              ArchiveManager& arcMgr,
                              size_t blockSize);
    /**
     * Old doRestore() implementation.
     *
     * @deprecated use doRestore().
     */
    void doRestoreOld();
    /**
     * Read blocks from multi archive manager and
     * Write the blocks to vmdk.
     * vmdk must be open before calling this.
     *
     * @param vddkCtrl vddk controller.
     * @param mArcMgr multi archive manager.
     * @param blockSize block size.
     */
    void writeBlocksToVmdk(VddkController& vddkCtrl,
                           MultiArchiveManager& mArcMgr,
                           size_t blockSize);
    /**
     * Test methods.
     */
    void doDumpTestFork();
    void doDumpTestNoFork();
};

#endif /* VMDKBACKUP_COMMAND_HPP_ */
