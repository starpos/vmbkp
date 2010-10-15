#include "file_lock.hpp"

int main()
{
    {
        ScopedFileLock lock("tmp/test_file_lock2.lock", false);
        ::printf("lock\n");
        ::sleep(1);
        ::printf("unlock\n");
    }
    
    return 0;
}
