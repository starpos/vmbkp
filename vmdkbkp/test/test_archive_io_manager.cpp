#include <string>
#include <sys/time.h>

#include "archive_io_manager.hpp"

double timePeriod(double begin, double end) {
    return end - begin;
}

struct Managers
{
    boost::shared_ptr<DumpInManager> dumpInP;
    boost::shared_ptr<DigestInManager> digestInP;
    boost::shared_ptr<DumpOutManager> dumpOutP;
    boost::shared_ptr<DigestOutManager> digestOutP;
    boost::shared_ptr<DumpOutManager> dumpOut2P;
    
    void setParallelManagers() {

        dumpInP = boost::shared_ptr<ParallelDumpInManager>
            (new ParallelDumpInManager);
        digestInP = boost::shared_ptr<ParallelDigestInManager>
            (new ParallelDigestInManager);
        dumpOutP = boost::shared_ptr<ParallelDumpOutManager>
            (new ParallelDumpOutManager);
        digestOutP = boost::shared_ptr<ParallelDigestOutManager>
            (new ParallelDigestOutManager);
        dumpOut2P = boost::shared_ptr<ParallelDumpOutManager>
            (new ParallelDumpOutManager);
    }

    void setSingleManagers() {

        dumpInP = boost::shared_ptr<SingleDumpInManager>
            (new SingleDumpInManager);
        digestInP = boost::shared_ptr<SingleDigestInManager>
            (new SingleDigestInManager);
        dumpOutP = boost::shared_ptr<SingleDumpOutManager>
            (new SingleDumpOutManager);
        digestOutP = boost::shared_ptr<SingleDigestOutManager>
            (new SingleDigestOutManager);
        dumpOut2P = boost::shared_ptr<SingleDumpOutManager>
            (new SingleDumpOutManager);
    }

    void init(const std::string& dumpInFn,
              const std::string& digestInFn,
              const std::string& dumpOutFn,
              const std::string& digestOutFn,
              const std::string& dumpOut2Fn) {

        dumpInP->init(dumpInFn);
        digestInP->init(digestInFn);
        dumpOutP->init(dumpOutFn);
        digestOutP->init(digestOutFn);
        dumpOut2P->init(dumpOut2Fn);
    }
};


int dumpSingle(const std::string& dumpInFn,
               const std::string& digestInFn,
               const std::string& dumpOutFn,
               const std::string& digestOutFn,
               const std::string& dumpOut2Fn)
{
    io::filtering_istream dumpIn;
    io::filtering_istream digestIn;
    io::filtering_ostream dumpOut;
    io::filtering_ostream digestOut;
    io::filtering_ostream dumpOut2;

    if (isGzipFileName(dumpInFn)) {
        dumpIn.push(io::gzip_decompressor());
    }
    if (isGzipFileName(digestInFn)) {
        digestIn.push(io::gzip_decompressor());
    }
    if (isGzipFileName(dumpOutFn)) {
        dumpOut.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
    }
    if (isGzipFileName(digestOutFn)) {
        digestOut.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
    }
    if (isGzipFileName(dumpOut2Fn)) {
        dumpOut2.push(io::gzip_compressor(io::gzip_params(io::gzip::best_speed)));
    }
    
    dumpIn.push(io::file_source(dumpInFn));
    digestIn.push(io::file_source(digestInFn));
    dumpOut.push(io::file_sink(dumpOutFn));
    digestOut.push(io::file_sink(digestOutFn));
    dumpOut2.push(io::file_sink(dumpOut2Fn));
    
    VmdkDumpHeader dumpH;
    VmdkDigestHeader digestH;

    dumpIn >> dumpH;
    digestIn >> digestH;
    dumpOut << dumpH;
    digestOut << digestH;
    dumpOut2 << dumpH;

    VmdkDumpBlock dumpB(dumpH.getBlockSize());
    VmdkDigestBlock digestB;

    double beginTime = 0;
    double endTime = 0;
    while (dumpIn.peek() != EOF && digestIn.peek() != EOF) {

        dumpIn >> dumpB;
        digestIn >> digestB;
        dumpOut << dumpB;
        digestOut << digestB;
        dumpOut2 << dumpB;

        VmdkDigestBlock checkB;
        checkB.set(dumpB);
        size_t oft = dumpB.getOffset();
        if (oft % 64 == 0) {
            ::printf("%zu", oft);
            beginTime = getTime();
        }
        if (checkB == digestB) {
            ::printf("."); fflush(stdout);
        } else {
            ::printf("X");
        }
        if (oft % 64 == 63) {
            endTime = getTime();
            ::printf("%.2fMB/s\n", 64.0 / timePeriod(beginTime, endTime));
        }
    }
    
    return 0;
}

int dumpTest(Managers& mgr)
{
    DumpInManager& dumpInMgr = *mgr.dumpInP;
    DigestInManager& digestInMgr = *mgr.digestInP;
    DumpOutManager& dumpOutMgr = *mgr.dumpOutP;
    DigestOutManager& digestOutMgr = *mgr.digestOutP;
    DumpOutManager& dumpOut2Mgr = *mgr.dumpOut2P;

    assert(dumpInMgr.isInit() &&
           digestInMgr.isInit() &&
           dumpOutMgr.isInit() &&
           digestOutMgr.isInit() &&
           dumpOut2Mgr.isInit());

    DumpHP dumpHP = dumpInMgr.getH();
    dumpOutMgr.putH(dumpHP);
    dumpOut2Mgr.putH(dumpHP);
    DigestHP digestHP = digestInMgr.getH();
    digestOutMgr.putH(digestHP);

    dumpInMgr.start();
    digestInMgr.start();
    dumpOutMgr.start();
    dumpOut2Mgr.start();
    digestOutMgr.start();

    double beginTime = 0;
    double endTime = 0;
    while (not dumpInMgr.isEnd() && not digestInMgr.isEnd()) {
        DumpBP dumpBP = dumpInMgr.getB();
        DigestBP digestBP = digestInMgr.getB();
        dumpOutMgr.putB(dumpBP);
        dumpOut2Mgr.putB(dumpBP);
        digestOutMgr.putB(digestBP);

        VmdkDigestBlock checkB;
        checkB.set(*dumpBP);

        size_t oft = dumpBP->getOffset();
        if (oft % 64 == 0) {
            ::printf("%zu", oft);
            beginTime = getTime();
        }
        if (checkB == *digestBP) {
            ::printf("."); fflush(stdout);
        } else {
            ::printf("X");
        }
        if (oft % 64 == 63) {
            endTime = getTime();
            ::printf("%.2fMB/s\n", 64.0 / timePeriod(beginTime, endTime));

            dumpInMgr.pause();
            digestInMgr.pause();
            dumpOutMgr.pause();
            dumpOut2Mgr.pause();
            digestOutMgr.pause();
            ::sleep(1);
            dumpInMgr.resume();
            digestInMgr.resume();
            dumpOutMgr.resume();
            dumpOut2Mgr.resume();
            digestOutMgr.resume();
        }
    }
    dumpInMgr.stop();
    digestInMgr.stop();
    dumpOutMgr.stop();
    dumpOut2Mgr.stop();
    digestOutMgr.stop();
    
    return 0;
}


int main(int argc, char* argv[])
{
    int ret;

    std::string a1(argv[1]);
    std::string a2(argv[2]);
    std::string a3(a1); a3.append(".out");
    std::string a4(a2); a4.append(".out");
    std::string a5(a1); a5.append(".out2");

    Managers mgr;
    
    double beginTime, endTime;
    double timeParallel, timeSingle;

    mgr.setParallelManagers();
    mgr.init(a1, a2, a3, a4, a5);
    beginTime = getTime();
    ret = dumpTest(mgr);
    endTime = getTime();
    timeParallel = timePeriod(beginTime, endTime);
    
    mgr.setSingleManagers();
    mgr.init(a1, a2, a3, a4, a5);
    beginTime = getTime();
    ret = dumpTest(mgr);
    endTime = getTime();
    timeSingle = timePeriod(beginTime, endTime);

    ::printf("parallel: %.2f\nsingle: %.2f\n", timeParallel, timeSingle);
    
    return 0;
}
