#include <iostream>
#include <unistd.h>

#include "fork_manager.hpp"
#include "stream_socket.hpp"

#include <boost/iostreams/filtering_stream.hpp>
#include <boost/iostreams/device/file_descriptor.hpp>

class TestForkManager
    : public ForkManager
{
    int id_;
    StreamSocket sock_;
    
    int run() {
        std::cout << sock_.recvMsg();

        char buf[256];
        ::sprintf(buf, "%d", id_);
        sock_.sendMsg(std::string("OK") + buf);

        ByteArrayPtr p = sock_.recvBuf();
        ByteArray& b = *p;

        size_t size = b.size();
        b.resize(size + 2);
        b.push_back('O');
        b.push_back('K');
        sock_.sendBuf(b);

        return 0;
    }

public:
    TestForkManager(int id) : id_(id), sock_(is_, os_) {}

    StreamSocket& getSock() {
        return sock_;
    }
};

int main()
{
    TestForkManager worker0(0);
    TestForkManager worker1(1);
    StreamSocket& sock0 = worker0.getSock();
    StreamSocket& sock1 = worker1.getSock();

    worker0.start();
    worker1.start();

    sock0.sendMsg("To worker 0.");
    sock1.sendMsg("To worker 1.");

    std::cout << sock0.recvMsg() << std::endl;
    std::cout << sock1.recvMsg() << std::endl;

    ByteArray b(1024, '_');

    sock0.sendBuf(b);
    sock1.sendBuf(b);

    ByteArrayPtr p;
    p = sock0.recvBuf();
    std::cout << std::string(&(*p)[0], p->size()) << std::endl;
    
    p = sock1.recvBuf();
    std::cout << std::string(&(*p)[0], p->size()) << std::endl;

    return 0;
}
