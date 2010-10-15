#include "file_lock.hpp"

int main()
{

    for (int i = 0; i < 100000; i ++) {
        ScopedFileLock lock("tmp/test_file_lock.lock");
    }
    
    return 0;
}
