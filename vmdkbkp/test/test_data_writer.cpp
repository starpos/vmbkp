#include <stdio.h>
#include <string>
#include <sstream>

#include <boost/shared_ptr.hpp>

#include "data_writer.hpp"

int main()
{
    DataWriter<std::string> writer(std::cout);
    writer.start();

    for (int i = 0; i < 100; i ++) {
        std::stringstream ss;
        ss << i << "\n";

        boost::shared_ptr<std::string> ssp(new std::string);
        *ssp = ss.str();
        writer.put(ssp);

        if (i % 20 == 19) {
            writer.pause();
            std::cout << std::endl;
            ::sleep(1);
            writer.resume();
        }
    }
    writer.stop();

    //std::cout << std::endl;

    return 0;
}
