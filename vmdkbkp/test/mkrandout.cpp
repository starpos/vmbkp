#include <iostream>

#include <stdlib.h>
#include <time.h>
#include <stdint.h>


int main(int argc, char *argv[])
{
    uint64_t blocksize = atol(argv[1]);
    uint64_t nblocks = atol(argv[2]);
    uint64_t count = 0;
    
    srand(time(0));
    while (count < blocksize * nblocks) {
        unsigned char c = static_cast<unsigned char>(
            static_cast<int>(
                (256.0 * rand() / (RAND_MAX + 1.0))));

        std::cout << c;
        ++ count;
    }

    return 0;
}
