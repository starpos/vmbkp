#include <stdio.h>
#include <assert.h>

#include <vector>

#include <boost/foreach.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>
#include <boost/thread/xtime.hpp>
#include <boost/bind.hpp>
#include <boost/random/mersenne_twister.hpp>
#include <boost/random/uniform_int.hpp>
#include <boost/random/variate_generator.hpp>

#include "queue.hpp"

boost::mt19937 gen__;

class Block
{
private:
    int id_;
    std::vector<unsigned char> blk_;

public:
    Block(int id, size_t size) : id_(id), blk_(size) {
        makeRandBlock();
    }

    void print() {
        ::printf("%d %02x %02x %02x %02x\n",
                 id_, blk_[0], blk_[1], blk_[2], blk_[3]);
    }

private:
    void makeRandBlock() {
        
        boost::uniform_int<> dist(0, 255);
        boost::variate_generator<boost::mt19937&, boost::uniform_int<> >
            getRandChar(gen__, dist);
    
        for (int i = 0; i < 4; i ++) {
            blk_[i] = static_cast<unsigned char>(getRandChar());
        }
    }
};

//typedef std::vector<unsigned char> Block;
typedef boost::shared_ptr<Block> BlockPtr;


void putWorker(Queue<BlockPtr>* bq)
{
    for (int i = 0; i < 100; i ++) {
        if (! bq->put(BlockPtr(new Block(i, 4)))) {
            ::printf("putWorker end abnormaly\n");
            return;
        }
    }
    ::printf("putWorker end\n");
}
void getWorker(Queue<BlockPtr>* bq)
{
    for (int i = 0; i < 100; i ++) {
        BlockPtr bp;
        if (bq->get(bp)) {
            bp->print();
        } else {
            ::printf("getWorker end abnormaly\n");
            return;
        }
    }
    ::printf("getWorker end\n");
}

typedef boost::shared_ptr<boost::thread> ThreadPtr;

int main()
{
    
    Queue<BlockPtr> bq(50);
    std::vector<ThreadPtr> putters;
    std::vector<ThreadPtr> getters;
    
    for (int i = 0; i < 10; i ++) {
        putters.push_back(ThreadPtr(new boost::thread(putWorker, &bq)));
    }
    for (int i = 0; i < 10; i ++) {
        getters.push_back(ThreadPtr(new boost::thread(getWorker, &bq)));
    }
    ::usleep(1000);
    bq.close();
    ::printf("stop queue %zu\n", bq.size());

    bq.clear();
    ::printf("clear queue %zu\n", bq.size());

    BOOST_FOREACH(ThreadPtr th, putters) { th->join(); }
    BOOST_FOREACH(ThreadPtr th, getters) { th->join(); }

    ::usleep(1000000);

    ::printf("start queue %zu\n", bq.size());
    bq.open();
    
    for (int i = 0; i < 10; i ++) {
        putters.push_back(ThreadPtr(new boost::thread(putWorker, &bq)));
    }
    for (int i = 0; i < 10; i ++) {
        getters.push_back(ThreadPtr(new boost::thread(getWorker, &bq)));
    }
    BOOST_FOREACH(ThreadPtr th, putters) { th->join(); }
    BOOST_FOREACH(ThreadPtr th, getters) { th->join(); }

    return 0;
}
