/**
 * @file
 * @brief Management of archive files.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#ifndef VMDKBACKUP_ARCHIVE_MANAGER_HPP_
#define VMDKBACKUP_ARCHIVE_MANAGER_HPP_

#include <fstream>

#include <boost/shared_ptr.hpp>
#include <boost/scoped_array.hpp>
#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/filter/gzip.hpp>
#include <boost/iostreams/device/file.hpp>

#include "header.hpp"
#include "bitmap.hpp"
#include "archive_io_manager.hpp"

namespace io = boost::iostreams;

/**
 * @brief This class manages dump/rdiff/digest streams and bmp data.
 *
 * <pre>
 * All streams are accessed sequentially.
 * Back-track does not occur at all.
 * </pre>
 */
class ArchiveManager
{
private:
    /* Input/output managers for each stream. */
    boost::shared_ptr<DumpInManager> dumpInMgrP_;
    boost::shared_ptr<DigestInManager> digestInMgrP_;
    boost::shared_ptr<DumpOutManager> dumpOutMgrP_;
    boost::shared_ptr<DigestOutManager> digestOutMgrP_;
    boost::shared_ptr<DumpOutManager> rdiffOutMgrP_;

    /* Input of chagned block bitmap. */
    std::ifstream changedBlockBitmapIn_;

    /* Open flag of each file. */
    bool isOpenDumpIn_;
    bool isOpenDumpOut_;
    bool isOpenDigestIn_;
    bool isOpenDigestOut_;
    bool isOpenRdiffOut_;

protected:
    /**
     * Confit data.
     * This is not initialized but referred in this and sub classses.
     */
    const ConfigData& cfg_;
    
public:
    /**
     * Contsructor.
     *
     * @exception Throw MyException.
     */
    ArchiveManager(const ConfigData& cfg);
    /**
     * Destructor.
     */
    ~ArchiveManager();

    /**
     * Check dumpIn is open.
     */
    bool isDumpInOpen() const;
    /**
     * Check digestIn is open.
     */
    bool isDigestInOpen() const;
    /**
     * Check dumpIn is available to read.
     */
    bool canReadFromDump();
    /**
     * Check digestIn is available to read.
     */
    bool canReadFromDigest();
    /**
     * Read dump header data from the input dump stream.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void readDumpHeader(VmdkDumpHeader& dumpH);
    /**
     * Read digest header data from the input digest stream.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void readDigestHeader(VmdkDigestHeader& digestH);
    /**
     * Write header information to output dump stream.
     *
     * @exception Throws ExceptionStack.
     */
    void writeDumpHeader(const VmdkDumpHeader& dumpH);
    /**
     * Write header information to output digest stream.
     *
     * @exception Throws ExceptionStack.
     */
    void writeDigestHeader(const VmdkDigestHeader& digestH);
    /**
     * Write header information to output rdiff stream.
     *
     * @exception Throws ExceptionStack.
     */
    void writeRdiffHeader(const VmdkDumpHeader& rdiffH);
    /**
     * Read a block from the dump.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void readFromDump(VmdkDumpBlock& dumpB);
    /**
     * Read a block from the digest.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void readFromDigest(VmdkDigestBlock& digestB);
    /**
     * Write block to the output dump stream.
     *
     * @param dumpB Block data to write.
     * @exception Throws ExceptionStack.
     */
    void writeToDump(const VmdkDumpBlock& dumpB);
    /**
     * Write digest to the output digest stream.
     *
     * @param digestB Digest data to write.
     * @exception Throw ExceptionStack.
     */
    void writeToDigest(const VmdkDigestBlock& digestB);
    /**
     * Write block to the output rdiff stream.
     *
     * @param rdiffB Block data to write.
     * @exception Throws ExceptionStack.
     */
    void writeToRdiff(const VmdkDumpBlock& rdiffB);
    /**
     * Read changed block bitmap.
     *
     * @exception Throw MyException, ExceptionStack.
     */
    void readChangedBlockBitmap(Bitmap& bmp);
    /**
     * Pause worker threads.
     * You must call this before calling fork().
     */
    void pause();
    /**
     * Resume worker threads.
     * You must call this after calling fork().
     */
    void resume();
    
    friend class TestArchiveManager;
    
private:
    /**
     * Check the streams are available for given mode.
     *
     * @exception Throws MyException.
     */
    void checkStreams();
};

/**
 * @brief Special ArchiveManager for dump command.
 */
class ArchiveManagerForDump
    : public ArchiveManager
{
public:
    /**
     * Constructor.
     */
    ArchiveManagerForDump(const ConfigData& cfg)
        : ArchiveManager(cfg) {}
    /**
     * Destructor.
     */
    ~ArchiveManagerForDump(){}
    
    /**
     * Read header information from the input dump/digest streams.
     * This is for dump command.
     *
     * @exception Throws MyException, ExceptionStack.
     */
    void readHeaders(
        VmdkDumpHeader& dumpH, VmdkDigestHeader& digestH);
    /**
     * Write header information to dump/rdiff/digest.
     *
     * @exception Throws ExceptionStack.
     */
    void writeHeaders(const VmdkDumpHeader& dumpH,
                      const VmdkDigestHeader& digestH,
                      const VmdkDumpHeader& rdiffH);
    /**
     * Set output header data.
     */
    void setHeaders(
        const VmdkInfo& vmdkInfo,
        const VmdkDumpHeader& prevDumpH, const VmdkDigestHeader& prevDigestH,
        VmdkDumpHeader& currDumpH, VmdkDigestHeader& currDigestH,
        VmdkDumpHeader& currRdiffH);
    /**
     * Read a block from the dump and the digest.
     * This is for dump command.
     *
     * @param dumpB result.
     * @param digestB result.
     * @exception Throws MyException, ExceptionStack.
     */
    void readFromStreams(
        VmdkDumpBlock& dumpB, VmdkDigestBlock& digestB);
    /**
     * Write dump/rdiff/digest to the output streams.
     * This is for dump command.
     *
     * @param prevDumpB Block data for rdiff.
     * @param prevDigestB Digest data for update-check.
     * @param currDumpB Block data to be written to dump.
     * @param currDigestB Digest data to be written.
     * @return True when previous and current block
     *         are different, or false.
     * @exception Throws ExceptionStack.
     */
    bool writeToStreams(
        const VmdkDumpBlock& prevDumpB, const VmdkDigestBlock& prevDigestB,
        const VmdkDumpBlock& currDumpB, const VmdkDigestBlock& currDigestB);
    /**
     * Read changed block bitmap data for incremental dump.
     *
     * @exception Throws ExceptionStack.
     */
    void readChangedBlockBitmap(Bitmap& bmp);
};

/**
 * @brief Manage multi dump/rdiff input streams.
 */
class MultiArchiveManager
{
private:
    const std::vector<std::string>& archiveList_;
    const size_t nArchives_;

    std::vector<boost::shared_ptr<DumpInManager> > dumpInMgrPs_;
    boost::scoped_array<io::filtering_istream> dumpIns_;
    std::vector<DumpHP> dumpHPs_;
    std::vector<DumpBP> dumpBPs_;
    std::vector<bool> dumpEofs_;

    uint64 offset_;

    /* Initialized in constructor. */
    uint64 blockSize_;
    uint64 diskSize_;
    ByteArray uuid_;
    VmdkDumpHeader dumpH_;
    
public:
    /**
     * Constructor.
     *
     * @param archiveList Archive dump/rdiff file list.
     * @exception MyException, ExceptionStack.
     */
    MultiArchiveManager(
        const std::vector<std::string>& archiveList);
    /**
     * Destructor.
     */
    ~MultiArchiveManager();

    /**
     * Get current block offset.
     */
    uint64 getOffset() const;
    /**
     * Get block size.
     */
    uint64 getBlockSize() const;
    /**
     * Get disk size (in unit of block).
     */
    uint64 getDiskSize() const;
    /**
     * Check the streams are completely consumed.
     */
    bool isEOF() const;
    /**
     * Read current block and increment offset.
     *
     * @param dumpB Read block.
     * @return True if read, or false.
     * @exception ExceptionStack.
     */
    bool readBlock(VmdkDumpBlock& dumpB);
    /**
     * Get dump header data of the streams.
     */
    void getDumpHeader(VmdkDumpHeader& dumpH);
    /**
     * Pause worker threads.
     * You must call this before calling fork().
     */
    void pause();
    /**
     * Resume worker threads.
     * You must call this after calling fork().
     */
    void resume();
};
    
#endif /* VMDKBACKUP_ARCHIVE_MANAGER_HPP_ */
