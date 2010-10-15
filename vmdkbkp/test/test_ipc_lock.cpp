#include <iostream>
#include <unistd.h>
#include <sys/wait.h>
#include "ipc_lock.hpp"

#define MUTEX_NAME "test_mutex"
#define SHM_NAME "test_shm"


void testSharedLock()
{
    IpcLock lk(MUTEX_NAME);
    IpcSharedMemory<int> shmInt(SHM_NAME);
    int& c = shmInt.get();

    c = 0;
    for (int i = 0; i < 5; i ++) {
        if (::fork() == 0) {

            for (int j = 0; j < 10; j ++) {
                lk.lock_sharable();
                std::cout << "proc " << i
                          << ": read " << c << std::endl;
                lk.unlock_sharable();
                ::usleep(10000);
            }
            ::exit(0);
        }
    }

    for (int i = 0; i < 100; i ++) {
        lk.lock();
        c ++;
        lk.unlock();
        ::usleep(1000);
    }

    int status;
    ::wait(&status);

    lk.remove();
}

int main()
{
    testSharedLock();
}
