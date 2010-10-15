/**
 * @file
 * @brief Unit test for ArchiveManager class defined in manager.hpp.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <iostream>

#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/filter/gzip.hpp>
#include <boost/iostreams/device/file.hpp>

#include <stdlib.h>
#include <assert.h>
#include <time.h>
#include <sys/time.h>

#include "serialize.hpp"
#include "header.hpp"
#include "util.hpp"
#include "bitmap.hpp"
#include "archive_manager.hpp"

#ifdef DEBUG
#define VERBOSE 1
#else
#define VERBOSE 0
#endif

#define BLOCK_SIZE 1048576 /* 1M bytes */

namespace io = boost::iostreams;

/**
 * @brief Manage all archie file names.
 */
struct ArchiveFileNames
{
    std::string dumpIn_;
    std::string digestIn_;
    std::string dumpOut_;
    std::string digestOut_;
    std::string bmpIn_;
    std::string rdiffOut_;

    ArchiveFileNames(const std::string& dumpIn,
                     const std::string& digestIn,
                     const std::string& dumpOut,
                     const std::string& digestOut,
                     const std::string& bmpIn,
                     const std::string& rdiffOut)
        : dumpIn_(dumpIn)
        , digestIn_(digestIn)
        , dumpOut_(dumpOut)
        , digestOut_(digestOut)
        , bmpIn_(bmpIn)
        , rdiffOut_(rdiffOut) {}
};

/**
 * @brief Test for ArchiveManager calss.
 */
class TestArchiveManager
{
public:
    /**
     * Config data (partially used) for ArchiveManager.
     */
    ConfigData cfg_;

    /**
     * Disk size in blocks.
     */
    size_t diskSize_;

    /**
     * Constructor.
     */
    TestArchiveManager(size_t diskSize)
        : diskSize_(diskSize) {}
    /**
     * Initialize config data.
     *
     * @param cmdStr command string like dump, restore.
     * @param modeStr mode string like full, diff, or incr.
     */
    void initializeConfigData(
        const std::string& cmdStr, const std::string& modeStr) {

        cfg_.programName = "test_archive_manager";
        cfg_.versionStr = "0.00";
        cfg_.cmdStr = cmdStr;
        cfg_.setCmd(cmdStr);
        cfg_.setMode(modeStr);

        cfg_.blocksize = BLOCK_SIZE;
        cfg_.nSectorsPerBlock = cfg_.blocksize / 512;
        assert(cfg_.blocksize % 512 == 0);
    }
    /**
     * Make header data of archives for test.
     *
     * @param dumpH dump header.
     * @param digestH digest header.
     */
    void makeHeaders(VmdkDumpHeader& dumpH, VmdkDigestHeader& digestH) {

        dumpH.initialize(diskSize_, BLOCK_SIZE, 2);
        dumpH.setFull(true);
        dumpH.setUUID();
        dumpH.setTimeStampNow();
        StringMap& map = dumpH.getMetadata();
        map["key1"] = "value1";
        map["key2"] = "value2";

        digestH.initialize(dumpH.getDiskSize(), dumpH.getBlockSize());
        digestH.setUUID(dumpH.getUUID());
        digestH.setTimeStamp(dumpH.getTimeStamp());

        assert(dumpH.getTimeStamp() == digestH.getTimeStamp());
        if (false) { /* debug */
            ::printf("timestamp of headers: %llu:%llu\n", 
                     (unsigned long long)dumpH.getTimeStamp(),
                     (unsigned long long)digestH.getTimeStamp());
        }
    }

    /**
     * Generate random integer
     * less than the specified upper value.
     *
     * @param upper the generated value < upper.
     * @return generated random integer.
     */
    int genRandomInt(int upper) {

        return static_cast<int>
            (static_cast<double>(upper) * rand()
             / (static_cast<double>(RAND_MAX) + 1.0));
    }
    /**
     * Generate random uint8 value.
     *
     * @return generated random value.
     */
    uint8 genRandomUint8() {

        return static_cast<uint8>(genRandomInt(256));
    }
    /**
     * Set the specified buffer with random bytes.
     *
     * @param buf buffer pointer to be set.
     * @param size buffer size in bytes.
     */
    void setRandomByteArray(uint8* buf, size_t size) {

        for (size_t i = 0; i < size; i ++) {
            buf[i] = genRandomUint8();
        }
    }

    /**
     * Make block data with random contents.
     *
     * @param offset block offset.
     * @param dumpB block data for dump.
     * @param digestB block data for digest.
     */
    void makeRandomBlock(uint64 offset,
                         VmdkDumpBlock& dumpB,
                         VmdkDigestBlock& digestB) {

        dumpB.setOffset(offset);
        setRandomByteArray(dumpB.getBuf(), dumpB.blockSize_);
        dumpB.setIsAllZero();
        digestB.set(dumpB);
    }
    /**
     * Make zero-block.
     *
     * @param offset block offset.
     * @param dumpB block data for dump.
     * @param digestB block data for digest.
     */
    void makeZeroBlock(uint64 offset,
                       VmdkDumpBlock& dumpB,
                       VmdkDigestBlock& digestB) {

        dumpB.setOffset(offset);
        dumpB.setAllZero();
        digestB.set(dumpB);
    }
    /**
     * Make random block in 66% or zero block in 33%.
     *
     * @param offset block offset.
     * @param dumpB block data for dump.
     * @param digestB block data for digest.
     */
    void makeBlock(uint64 offset,
                   VmdkDumpBlock& dumpB,
                   VmdkDigestBlock& digestB) {

        int flag = genRandomInt(3);
        if (flag == 0) {
            makeRandomBlock(offset, dumpB, digestB);
        } else {
            makeZeroBlock(offset, dumpB, digestB);
        }
    }

    /**
     * Initialize gziped input stream.
     */
    void initGzipedIstream(io::filtering_istream& in,
                           const std::string& filename) {

        if (isGzipFileName(filename)) {
            in.push(io::gzip_decompressor());
        }
        in.push(io::file_source(filename));
    }
            
    /**
     * Initialize gziped output stream.
     */
    void initGzipedOstream(io::filtering_ostream& out,
                           const std::string& filename) {

        if (isGzipFileName(filename)) {
            out.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
        }
        out.push(io::file_sink(filename));
    }

    /**
     * Make full dump and digest file.
     *
     * @param dumpOutFn File name of dump out.
     * @param digestOutFn File name of digest out.
     */
    void makeFullArchive(const std::string& dumpOutFn,
                         const std::string& digestOutFn) {

        if (VERBOSE)
            ::printf("makeFullArchive begin\n");
        
        io::filtering_ostream dumpOut, digestOut;
        initGzipedOstream(dumpOut, dumpOutFn);
        initGzipedOstream(digestOut, digestOutFn);

        VmdkDumpHeader dumpH;
        VmdkDigestHeader digestH;
        makeHeaders(dumpH, digestH);
        dumpOut << dumpH;
        digestOut << digestH;

        VmdkDumpBlock dumpB(BLOCK_SIZE);
        VmdkDigestBlock digestB;
        
        for (uint64 offset = 0; offset < dumpH.getDiskSize(); offset ++) {

            makeBlock(offset, dumpB, digestB);
            
            dumpOut << dumpB;
            digestOut << digestB;
        }

        dumpOut.reset();
        digestOut.reset();
        
        if (VERBOSE)
            ::printf("makeFullArchive end\n");
    }

    /**
     * Make updated archives.
     * The updates will be generated randomly.
     *
     * @param dumpInFn File name of dump in.
     * @param digestInFn,File name of digest in.
     * @param dumpOutFn File name of dump out (generated).
     * @param digestOutFn File name of digest out (generated).
     * @param bmpOutFn File name of changed block bitmap out (generated).
     * @param rdiffOutFn File name of rdiff out (generated).
     */
    void makeUpdatedArchive(const std::string& dumpInFn,
                            const std::string& digestInFn,
                            const std::string& dumpOutFn,
                            const std::string& digestOutFn,
                            const std::string& bmpOutFn,
                            const std::string& rdiffOutFn) {
        if (VERBOSE)
            ::printf("makeUpdatedArchive begin\n");

        io::filtering_istream dumpIn, digestIn;
        io::filtering_ostream dumpOut, digestOut, rdiffOut;
        initGzipedIstream(dumpIn, dumpInFn);
        initGzipedIstream(digestIn, digestInFn);
        initGzipedOstream(dumpOut, dumpOutFn);
        initGzipedOstream(digestOut, digestOutFn);
        initGzipedOstream(rdiffOut, rdiffOutFn);
        
        std::ofstream bmpOut(bmpOutFn.c_str());
        
        /* Initialize headers. */
        VmdkDumpHeader prevDumpH, currDumpH, rdiffH;
        VmdkDigestHeader prevDigestH, currDigestH;

        dumpIn >> prevDumpH;
        currDumpH.copyDataFrom(prevDumpH);
        currDumpH.setTimeStampNow();
        dumpOut << currDumpH;

        rdiffH.copyDataFrom(prevDumpH);
        rdiffH.setFull(false);
        rdiffOut << rdiffH;

        digestIn >> prevDigestH;
        currDigestH.copyDataFrom(prevDigestH);
        currDigestH.setTimeStamp(currDumpH.getTimeStamp());
        digestOut << currDigestH;

        /* Initialize bmp. */
        Bitmap bmp(prevDumpH.getDiskSize());

        /* Write each block */
        VmdkDumpBlock prevDumpB(BLOCK_SIZE), currDumpB(BLOCK_SIZE);
        VmdkDigestBlock prevDigestB, currDigestB;

        for (uint64 offset = 0; offset < prevDumpH.getDiskSize(); offset ++) {

            dumpIn >> prevDumpB;
            digestIn >> prevDigestB;
            
            int flag = genRandomInt(3);
            if (flag == 0) {

                makeBlock(offset, currDumpB, currDigestB);
                dumpOut << currDumpB;
                digestOut << currDigestB;

                /* changed */
                if (prevDigestB != currDigestB) {
                    rdiffOut << prevDumpB;
                    bmp.set(offset);
                }
                
            } else {
                /* unchanged */
                dumpOut << prevDumpB;
                digestOut << prevDigestB;
            }
        }
        
        bmpOut << bmp;
        
        if (VERBOSE)
            ::printf("makeUpdatedArchive end\n");
    }

    /**
     * Make digest data form full dump.
     *
     * @param dumpInFn File name of dump in.
     * @param digestOutFn File name of digest out.
     */
    void makeDigestFromDump(const std::string& dumpInFn,
                            const std::string& digestOutFn) {

        if (VERBOSE)
            ::printf("makeDigestFromDump begin\n");
        
        io::filtering_istream dumpIn;
        io::filtering_ostream digestOut;
        initGzipedIstream(dumpIn, dumpInFn);
        initGzipedOstream(digestOut, digestOutFn);

        VmdkDumpHeader prevDumpH;
        VmdkDigestHeader currDigestH;
        dumpIn >> prevDumpH;
        currDigestH.set(prevDumpH);
        digestOut << currDigestH;

        VmdkDumpBlock prevDumpB(BLOCK_SIZE);
        VmdkDigestBlock currDigestB;

        for (uint64 offset = 0; offset < prevDumpH.getDiskSize(); offset ++) {

            dumpIn >> prevDumpB;
            currDigestB.set(prevDumpB);
            digestOut << currDigestB;
        }

        if (VERBOSE)
            ::printf("makeDigestFromDump end\n");
    }

    /**
     * Check the dump and the digest are correponding ones.
     *
     * @param dumpInFn File name of dump in to be check.
     * @param digestInFn File name of digest in to be check.
     */
    bool checkDumpAndDigest(const std::string& dumpInFn,
                            const std::string& digestInFn) {

        if (VERBOSE)
            ::printf("checkDumpAndDigest begin\n");
        
        io::filtering_istream dumpIn, digestIn;
        initGzipedIstream(dumpIn, dumpInFn);
        initGzipedIstream(digestIn, digestInFn);
        
        VmdkDumpHeader dumpH;
        VmdkDigestHeader digestH;
        try {
            dumpIn >> dumpH;
            digestIn >> digestH;
        } catch (ExceptionStack& e) {
            e.add("header...", __FILE__, __LINE__);
            throw;
        }

        VmdkDumpBlock dumpB(BLOCK_SIZE);
        VmdkDigestBlock digestB, digestCheck;

        try {
            for (uint64 offset = 0; offset < dumpH.getDiskSize(); offset ++) {

                if (dumpIn.peek() != EOF) {
                    dumpIn >> dumpB;
                } else {
                    break;
                }
                digestIn >> digestB;

                while (offset < dumpB.getOffset()) {
                    digestIn >> digestB;
                    offset ++;
                }
                assert(offset == dumpB.getOffset());

                digestCheck.set(dumpB);
                if (digestB != digestCheck) {
                    return false;
                }
            }
        } catch (ExceptionStack& e) {
            e.add("block...", __FILE__, __LINE__);
            throw;
        }

        if (VERBOSE)
            ::printf("checkDumpAndDigest end\n");
        
        return true;
    }

    /**
     * Check the specified two dump files whether their contents
     * are the same or not.
     * This will check only block contents, not metadata etc.
     *
     * @param dump1Fn File name of 1st dump in.
     * @param dump2Fn File name of 2nd dump in.
     * @return true when they are the same.
     */
    bool checkDumpAndDump(const std::string& dump1Fn,
                          const std::string& dump2Fn) {

        if (VERBOSE)
            ::printf("checkDumpAndDump begin\n");
        
        io::filtering_istream dump1In, dump2In;
        initGzipedIstream(dump1In, dump1Fn);
        initGzipedIstream(dump2In, dump2Fn);

        VmdkDumpHeader dump1H, dump2H;
        dump1In >> dump1H;
        dump2In >> dump2H;

        assert(dump1H.getDiskSize() == dump2H.getDiskSize());
        assert(dump1H.getBlockSize() == dump2H.getBlockSize());
        assert(dump1H.isFull() == dump2H.isFull());

        VmdkDumpBlock dump1B(BLOCK_SIZE), dump2B(BLOCK_SIZE);
        while (dump1In.peek() != EOF && dump2In.peek() != EOF) {
            dump1In >> dump1B;
            dump2In >> dump2B;

            if (dump1B != dump2B) {
                return false;
            }
        }

        if (VERBOSE)
            ::printf("checkDumpAndDump end\n");
        
        return true;
    }

    /**
     * Check the specified two digest files whether their contents
     * are the same or not.
     * This will check only block contents, not metadata etc.
     *
     * @param digest1Fn File name of 1st digest in.
     * @param digest2Fn File name of 2nd digest in.
     * @return true when they are the same.
     */
    bool checkDigestAndDigest(const std::string& digest1Fn,
                              const std::string& digest2Fn) {

        if (VERBOSE)
            ::printf("checkDigestAndDigest begin\n");
            
        io::filtering_istream digest1In, digest2In;
        initGzipedIstream(digest1In, digest1Fn);
        initGzipedIstream(digest2In, digest2Fn);
        
        VmdkDigestHeader digest1H, digest2H;
        digest1In >> digest1H;
        digest2In >> digest2H;

        assert(digest1H.getDiskSize() == digest2H.getDiskSize());
        assert(digest1H.getBlockSize() == digest2H.getBlockSize());

        VmdkDigestBlock digest1B, digest2B;
        while (digest1In.peek() != EOF && digest2In.peek() != EOF) {
            digest1In >> digest1B;
            digest2In >> digest2B;

            if (digest1B != digest2B) {
                return false;
            }
        }
        if (VERBOSE)
            ::printf("checkDigestAndDigest end\n");
        
        return true;
    }

    /**
     * Create initial data for test.
     */
    void createInitialDataForTest() {

        makeFullArchive("tmp/dump1", "tmp/digest1");
        makeUpdatedArchive("tmp/dump1", "tmp/digest1",
                           "tmp/dump2", "tmp/digest2",
                           "tmp/bmp2", "tmp/rdiff1");
        makeDigestFromDump("tmp/dump1", "tmp/digest1a");
        makeDigestFromDump("tmp/dump2", "tmp/digest2a");
    }

    /**
     * Run dump test.
     *
     * @param isIncremental true for incr mode, or diff mode.
     * @param psudoVmdkFn File name of dump that emulates vmdk file.
     * @param af File name of all archives.
     */
    void testDump(bool isIncremental,
                  const std::string& pseudoVmdkFn,
                  const ArchiveFileNames& af) {

        
        ::printf("testDump(isIncremental = %d) start.\n",
                 (int)isIncremental);

        io::filtering_istream pseudoVmdkIn;
        initGzipedIstream(pseudoVmdkIn, pseudoVmdkFn);
        
        initializeConfigData("dump", (isIncremental ? "incr" : "diff"));
        cfg_.dumpInFileName = af.dumpIn_;
        cfg_.digestInFileName = af.digestIn_;
        cfg_.dumpOutFileName = af.dumpOut_;
        cfg_.digestOutFileName = af.digestOut_;
        cfg_.bmpInFileName = af.bmpIn_;
        cfg_.rdiffOutFileName = af.rdiffOut_;

        VmdkDumpHeader dumpH, rdiffH;
        VmdkDigestHeader digestH;

        try {
            pseudoVmdkIn >> dumpH; /* read and abondon */
            
            ArchiveManager archMgr(cfg_);
            archMgr.readDumpHeader(dumpH);
            archMgr.readDigestHeader(digestH);

            archMgr.writeDumpHeader(dumpH);
            archMgr.writeDigestHeader(digestH);
            rdiffH.copyDataFrom(dumpH);
            rdiffH.setFull(false);
            archMgr.writeRdiffHeader(rdiffH);

            Bitmap bmp(dumpH.getDiskSize());
            if (isIncremental) {
                archMgr.readChangedBlockBitmap(bmp);
            }
            
            VmdkDumpBlock prevDumpB(BLOCK_SIZE), currDumpB(BLOCK_SIZE);
            VmdkDigestBlock prevDigestB, currDigestB;
            
            for (uint64 oft = 0; oft < dumpH.getDiskSize(); oft ++) {
                archMgr.readFromDump(prevDumpB);
                archMgr.readFromDigest(prevDigestB);

                pseudoVmdkIn >> currDumpB; /* read vmdk block */

                if (isIncremental) {
                    if (bmp.get(oft)) {
                        currDigestB.set(currDumpB);
                        archMgr.writeToDump(currDumpB);
                        archMgr.writeToDigest(currDigestB);
                        if (currDigestB != prevDigestB) {
                            archMgr.writeToRdiff(prevDumpB);
                        }
                    } else {
                        archMgr.writeToDump(prevDumpB);
                        archMgr.writeToDigest(prevDigestB);
                    }
                } else {
                    currDigestB.set(currDumpB);
                    if (currDigestB != prevDigestB) {
                        archMgr.writeToDump(currDumpB);
                        archMgr.writeToDigest(currDigestB);
                        archMgr.writeToRdiff(prevDumpB);
                    } else {
                        archMgr.writeToDump(prevDumpB);
                        archMgr.writeToDigest(prevDigestB);
                    }
                }
            }
            
        } catch (const MyException& e) {
            e.print();
            throw;
        } catch (ExceptionStack& e) {
            e.add("err", __FILE__, __LINE__);
            throw;
        }
        
        
        ::printf("testDump(isIncremental = %d) end.\n",
                 (int)isIncremental);
    }

};

/**
 * Time stamp test.
 */
void testTimeStamp()
{
    struct timeval tv;
    struct timezone tz;
    time_t now = ::time(0);
    gettimeofday(&tv, &tz);
    ::printf("%lld\n%lld\n",
             (long long)tv.tv_sec,
             (long long)now);


    struct tm timeM;
    ::gmtime_r(&now, &timeM);
    std::string timeStr = ::asctime(&timeM);
    ::printf("%s\n", timeStr.c_str());

    ::localtime_r(&now, &timeM);
    std::string timeStr2 = ::asctime(&timeM);
    ::printf("%s\n", timeStr2.c_str());

    time_t now2 = ::mktime(&timeM);

    ::printf("%lld\n%lld\n",
             (long long)now, (long long)now2);

    TimeStamp a, b;

    a.setTimeStamp();
    b.setTimeStamp(a.getTimeStamp());

    ::printf("%s\n%s\n",
             a.getTimeStampStr().c_str(),
             b.getTimeStampStr().c_str());
    ::printf("%llu\n%llu\n",
             (unsigned long long)a.getTimeStamp(),
             (unsigned long long)b.getTimeStamp());
    
    assert(a == b);
    
}

/**
 * Main function of the test.
 */
int main(int argc, char *argv[])
{
    ::printf("===================="
             "Test ArchiveManager class begin"
             "====================\n"
             "Create test archives of two generations.\n"
             "Then execute pseudo diff/incr dump "
             "using ArchiveManager functionalities.\n"
             "Finally, check the output files are correct.\n");

    ::srand(::time(0));

    if (argc != 2) {
        ::printf("Specify disk size to test.\n");
        return 0;
    }
    
    size_t diskSize = atol(argv[1]);
    
    for (int i = 0; i < 1; i ++) {

        ::printf("trial %d...", i);
        ::fflush(stdout);
    
        TestArchiveManager t(diskSize);
        t.createInitialDataForTest();
        try {
            /* diff dump test. */
            ArchiveFileNames a1("tmp/dump1",
                                "tmp/digest1",
                                "tmp/out/dump2_diff",
                                "tmp/out/digest2_diff",
                                "",
                                "tmp/out/rdiff1_diff");
            t.testDump(false, "tmp/dump2", a1);
            ::printf("done\n");
            assert(t.checkDumpAndDigest(a1.rdiffOut_, a1.digestIn_));
            assert(t.checkDumpAndDigest(a1.dumpOut_, "tmp/digest2"));
            assert(t.checkDumpAndDigest("tmp/dump2", a1.digestOut_));
            assert(t.checkDumpAndDump("tmp/dump2", a1.dumpOut_));
            assert(t.checkDigestAndDigest("tmp/digest2", a1.digestOut_));

            /* incr dump test. */
            ArchiveFileNames a2("tmp/dump1",
                                "tmp/digest1",
                                "tmp/out/dump2_incr",
                                "tmp/out/digest2_incr",
                                "tmp/bmp2",
                                "tmp/out/rdiff1_incr");
            t.testDump(true, "tmp/dump2", a2);
            assert(t.checkDumpAndDigest(a2.rdiffOut_, a2.digestIn_));
            assert(t.checkDumpAndDigest(a2.dumpOut_, "tmp/digest2"));
            assert(t.checkDumpAndDigest("tmp/dump2", a2.digestOut_));
            assert(t.checkDumpAndDump("tmp/dump2", a2.dumpOut_));
            assert(t.checkDigestAndDigest("tmp/digest2", a2.digestOut_));

            /* check diff result and incr result. */
            assert(t.checkDumpAndDump(a1.dumpOut_, a2.dumpOut_));
            assert(t.checkDigestAndDigest(a1.digestOut_, a2.digestOut_));
            assert(t.checkDumpAndDump(a1.rdiffOut_, a2.rdiffOut_));

            ::printf("OK\n");
            
        } catch (const ExceptionStack& e) {
            e.print();
        }
    }
    
    ::printf("===================="
             "Test ArchiveManager class done"
             "====================\n");
    return 0;
}

/* end of file */
