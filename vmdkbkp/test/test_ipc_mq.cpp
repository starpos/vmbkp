#include <iostream>
#include <unistd.h>
#include "ipc_mq.hpp"

#define MQ_NAME "test_mq"

int main()
{
    if (::fork() == 0) {

        IpcMessageQueue<int> mq(MQ_NAME);
        for (int i = 0; i < 32; i ++) {
            mq.put(i);
        }
        
    } else {
        IpcMessageQueue<int> mq(MQ_NAME, 16);
        
        int a;
        for (int i = 0; i < 32; i ++) {
            mq.get(a);
            std::cout << a << std::endl;
        }
    }
    
    return 0;
}
