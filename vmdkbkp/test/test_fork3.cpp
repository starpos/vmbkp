#include <stdio.h>
#include <assert.h>
#include <stdlib.h>

#include "fork_manager.hpp"

#define BUF_SIZE 256

/**
 * @brief TestForkManager
 */
class TestForkManager
    : public ForkManager
{
    int run() {
        if (not isChild_) { return 1; }
        char buf[BUF_SIZE];
        
        /* Get command and execute it. */
        while (true)
        {
            ::memset(buf, 0, BUF_SIZE);
            is_.read(buf, BUF_SIZE);
            std::string req(buf);

            ::printf("req: %s\n", buf);
            ::fflush(stdout);
            
            if (req == "hello") {
                os_.write("hello", BUF_SIZE);
                os_.flush();
            } else if (req == "end") {
                os_.write("OK", BUF_SIZE);
                os_.flush();
                break;
            } else {
                os_.write("error", BUF_SIZE);
                os_.flush();
            }
        }
        
        return 0;
    }
};

int main()
{
    char buf[BUF_SIZE];

    TestForkManager forkMgr, forkMgr2;
    forkMgr.start();
    forkMgr2.start();

    std::istream& is = forkMgr.getIstream();
    std::ostream& os = forkMgr.getOstream();
    std::istream& is2 = forkMgr2.getIstream();
    std::ostream& os2 = forkMgr2.getOstream();
    
    os.write("hello", BUF_SIZE);
    os.flush();
    ::memset(buf, 0, BUF_SIZE);
    is.read(buf, BUF_SIZE);
    printf("res: %s\n", buf);

    os.write("end", BUF_SIZE);
    os.flush();
    ::memset(buf, 0, BUF_SIZE);
    is.read(buf, BUF_SIZE);
    ::printf("res: %s\n", buf);

    os2.write("end", BUF_SIZE);
    os2.flush();
    ::memset(buf, 0, BUF_SIZE);
    is2.read(buf, BUF_SIZE);
    ::printf("res2: %s\n", buf);
    
    return 0;
}
