#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include <sys/time.h>

#include "fork_manager.hpp"
#include "util.hpp"

#define BUFFER_SIZE (64 * 1024)
#define TOTAL_SIZE (1024 * 1024 * 1024)

/**
 * @brief TestForkManager
 */
class TestForkManager
    : public ForkManager
{
    int run() {

        if (not isChild_) { return 1; }
        char buf[BUFFER_SIZE];
        const size_t count = TOTAL_SIZE/BUFFER_SIZE;
        
        for (size_t i = 0; i < count; i ++) {
            os_.write(buf, BUFFER_SIZE);
            os_.flush();
        }
        for (size_t i = 0; i < count; i ++) {
            is_.read(buf, BUFFER_SIZE);
        }
        return 0;
    }
};

int main()
{
    char buf[BUFFER_SIZE];
    TestForkManager forkMgr;
    std::istream& is = forkMgr.getIstream();
    std::ostream& os = forkMgr.getOstream();
    double b1,e1,b2,e2;
    const size_t count = TOTAL_SIZE/BUFFER_SIZE;

    forkMgr.start();
    
    b1 = getTime();
    for (size_t i = 0; i < count; i ++) {
        is.read(buf, BUFFER_SIZE);
    }
    e1 = getTime();

    b2 = getTime();
    for (size_t i = 0; i < count; i ++) {
        os.write(buf, BUFFER_SIZE);
        os.flush();
    }
    e2 = getTime();
    
    int status;
    ::wait(&status);

    ::printf("read from child %.2f MiB/s\n"
             "write to child  %.2f MiB/s\n",
             TOTAL_SIZE/(1024 * 1024)/(e1 - b1),
             TOTAL_SIZE/(1024 * 1024)/(e2 - b2));
    
    return 0;
}
