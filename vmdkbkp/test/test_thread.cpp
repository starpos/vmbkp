#include <stdio.h>
#include <unistd.h>

#include <boost/thread.hpp>
#include <boost/thread/xtime.hpp>

typedef boost::mutex::scoped_lock Lock;

struct SharedData
{
    boost::mutex mutex;
    boost::condition_variable cond;
    bool isWorkerAllowed;
    int id;
};

void worker(SharedData* sd)
{
    try {
        while (true) {
            Lock lk(sd->mutex);
            if (not sd->isWorkerAllowed) {
                sd->cond.wait(lk);
            }

            //::printf("worker: %d\n", sd->id);
            sd->id ++;
            sd->cond.notify_one();
        }
    } catch (const boost::thread_interrupted& e) {

        ::printf("worker: catch thread interrupted exception\n");
    }

    ::printf("worker: end\n");
}

int main()
{
    SharedData sd;
    sd.isWorkerAllowed = false;
    boost::thread th(worker, &sd);

    ::sleep(1);
    {
        Lock lk(sd.mutex);
        sd.isWorkerAllowed = false;
    }
    
    {
        Lock lk(sd.mutex);
        sd.isWorkerAllowed = true;
        ::printf("master: %d\n", sd.id);
        sd.cond.notify_one();
    }
    ::sleep(1);

    {
        Lock lk(sd.mutex);
        sd.isWorkerAllowed = false;
    }
    
    th.interrupt();
    {
        Lock lk(sd.mutex);
        ::printf("master: %d\n", sd.id);
    }
    th.join();
    ::printf("parent: %d\n", sd.id);
    
    return 0;
}
