#include <time.h>
#include <sys/time.h>

#include "util.hpp"
#include "ipc_lock_manager.hpp"

int main()
{
    LockManagerClient::srand();

    double t1, t2, t3;

    try {
        LockManagerClient lockMgr(LOCK_MANAGER_MQ_NAME, "vmdkbkp_lock");

        t1 = getTime();
        for (size_t i = 0; i < 10000; i ++) {
            lockMgr.lock();
            lockMgr.unlock();
        }
        t2 = getTime();

        for (size_t i = 0; i < 10000; i ++) {
            lockMgr.lock_sharable();
            lockMgr.unlock_sharable();
        }
        t3 = getTime();
        
    } catch (boost::interprocess::interprocess_exception& e) {
        std::cout << e.what();
    }

    ::printf("ex lock/unlock: %.03f\n"
             "sh lock/unlock: %.03f\n",
             t2 - t1, t3 - t2);

    t1 = getTime();
    for (size_t i = 0; i < 10000; i ++) {
        bool isExclusive = true;
        ScopedResourceLock lk("vmdkbkp_lock", isExclusive);
    }
    t2 = getTime();
    for (size_t i = 0; i < 10000; i ++) {
        bool isExclusive = false;
        ScopedResourceLock lk("vmdkbkp_lock", isExclusive);
    }
    t3 = getTime();

    ::printf("ex scoped lock: %.03f\n"
             "sh scoped lock: %.03f\n",
             t2 - t1, t3 - t2);
    return 0;
}
