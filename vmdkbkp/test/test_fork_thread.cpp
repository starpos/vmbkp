#include <stdio.h>
#include <unistd.h>
#include <signal.h>
#include <assert.h>
#include <stdlib.h>
#include <sys/wait.h>

#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>
#include <boost/thread/xtime.hpp>

typedef boost::mutex::scoped_lock Lock;

typedef boost::mutex Mutex;
typedef boost::shared_ptr<Mutex> MutexP;
typedef boost::condition_variable Cond;
typedef boost::shared_ptr<Cond> CondP;

struct SharedData
{
    MutexP mutexP;
    CondP condP;
    bool isWorkerAllowed;
    int id;
    SharedData() : isWorkerAllowed(false), id(0) {}
};

void worker(SharedData* sd)
{
    ::printf("worker start\n");
    try {
        while (true) {

            Lock lk(*sd->mutexP);
            if (not sd->isWorkerAllowed) {
                ::printf("waiting...begin\n");
                sd->condP->wait(lk);
                ::printf("waiting...end\n");
            }

            //::printf("worker: %d\n", sd->id);
            sd->id ++;
            sd->condP->notify_one();
        }
    } catch (const boost::thread_interrupted& e) {

        ::printf("worker: catch thread interrupted exception\n");
    }

    ::printf("worker: end\n");
}

void initSD(SharedData& sd)
{
    sd.mutexP = MutexP(new Mutex);
    sd.condP = CondP(new Cond);
    sd.isWorkerAllowed = false;
}

void finalizeSD(SharedData& sd)
{
    sd.mutexP = MutexP();
    sd.condP = CondP();
    sd.isWorkerAllowed = false;
}

int main()
{
    typedef boost::shared_ptr<boost::thread> ThreadPtr;
    SharedData sd;

    
    initSD(sd);
    ThreadPtr thP(new boost::thread(worker, &sd));

    ::sleep(1);
    {
        Lock lk(*sd.mutexP);
        sd.isWorkerAllowed = false;
        sd.condP->notify_one();
    }
    
    {
        Lock lk(*sd.mutexP);
        sd.isWorkerAllowed = true;
        ::printf("master: %d\n", sd.id);
        sd.condP->notify_one();
    }
    ::sleep(1);

    {
        Lock lk(*sd.mutexP);
        sd.isWorkerAllowed = false;
        sd.condP->notify_one();
    }

    thP->interrupt();
    thP->join();
    thP = ThreadPtr();
    //finalizeSD(sd);
    
    int pid;
    ::sigignore(SIGCHLD);
    if ((pid = ::fork()) == 0) {
        /* child */
        //thP->interrupt();
        //thP->join();
        ::printf("child: %d\n", sd.id);
        ::sleep(5);
        ::printf("child end\n");
        ::exit(0);
    } else {
        /* parent */
    }
    ::printf("parent: %d\n", sd.id);

    initSD(sd);
    thP = ThreadPtr(new boost::thread(worker, &sd));
    {
        Lock lk(*sd.mutexP);
        sd.isWorkerAllowed = true;
        sd.condP->notify_one();
    }
    ::sleep(1);
    {
        Lock lk(*sd.mutexP);
        ::printf("master: %d\n", sd.id);
        sd.isWorkerAllowed = false;
        sd.condP->notify_one();
    }
    thP->interrupt();
    thP->join();
    thP = ThreadPtr();
    //finalizeSD(sd);
    ::printf("parent: %d\n", sd.id);

    int status;
    ::wait(&status);
    
    return 0;
}
