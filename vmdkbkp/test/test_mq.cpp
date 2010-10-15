#include <iostream>

#include <unistd.h>
#include <signal.h>

#include <boost/interprocess/ipc/message_queue.hpp>
#include <boost/interprocess/sync/named_mutex.hpp>
#include <boost/interprocess/sync/named_condition.hpp>
#include <boost/interprocess/sync/scoped_lock.hpp>
#include <boost/interprocess/sync/sharable_lock.hpp>
#include <boost/interprocess/shared_memory_object.hpp>
#include <boost/interprocess/mapped_region.hpp>

namespace bipc = boost::interprocess;


typedef bipc::message_queue MQ;
typedef bipc::named_mutex IpcMutex;
typedef bipc::named_condition IpcCond;
typedef bipc::scoped_lock<IpcMutex> IpcScopedLock;
typedef bipc::sharable_lock<IpcMutex> IpcSharableLock;
typedef bipc::interprocess_exception IpcException;

typedef bipc::shared_memory_object IpcSharedMemory;
typedef bipc::mapped_region IpcMappedRegion;


#define MQ_NAME "test_mq"
#define MUTEX_NAME "test_mutex"
#define COND_NAME MUTEX_NAME"_cond"
#define SHM_NAME "test_shm"

int main()
{
    int forked;
    try {
        /* Message queue requires fixed size of data. */
        MQ mq_g(bipc::open_or_create, MQ_NAME, 16, sizeof(int));
        IpcMutex mutex_g(bipc::open_or_create, MUTEX_NAME);
        IpcCond cond_g(bipc::open_or_create, COND_NAME);

        IpcSharedMemory shm_g(bipc::open_or_create, SHM_NAME,
                              bipc::read_write);
        shm_g.truncate(1);
        IpcMappedRegion region_g(shm_g, bipc::read_write);
        char *cp = static_cast<char*>(region_g.get_address());
        char& c = *cp;
        c = 0;
        
        std::size_t sz;
        unsigned int pri;

        forked = ::fork();
        if (forked == 0) {
            /* child */
            MQ mq(bipc::open_only, MQ_NAME);
            IpcMutex mutex(bipc::open_only, MUTEX_NAME);
            IpcCond cond(bipc::open_only, COND_NAME);
            
            int num;
            for (int i = 0; i < 32; i ++) {
                mq.receive(&num, sizeof(num), sz, pri);
                std::cout << "recv " << num << std::endl;
            }

            ::sleep(1);
            {
                IpcScopedLock lk(mutex);
                c = 1;
                cond.notify_one();
                std::cout << "child finished" << std::endl;
            }

            ::exit(0);
        } else {
            /* parent */
            MQ mq(bipc::open_only, MQ_NAME);
            IpcMutex mutex(bipc::open_only, MUTEX_NAME);
            IpcCond cond(bipc::open_only, COND_NAME);
            
            for (int i = 0; i < 32; i ++) {
                mq.send(&i, sizeof(i), 0);
                std::cout << "send " << i << std::endl;
            }
            
            {
                IpcScopedLock lk(mutex);
                while (c == 0) {
                    cond.wait<IpcScopedLock>(lk);
                }
                std::cout << "parent finished" << std::endl;
            }
        }
    } catch (IpcException& ex) {
        std::cout << forked << ex.what() << std::endl;
    }
    MQ::remove(MQ_NAME);
    IpcMutex::remove(MUTEX_NAME);
    IpcCond::remove(COND_NAME);
    IpcSharedMemory::remove(SHM_NAME);
    return 0;
}
