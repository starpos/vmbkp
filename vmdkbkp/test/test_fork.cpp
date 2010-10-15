#include <stdio.h>
#include <unistd.h>
#include <signal.h>
#include <assert.h>
#include <stdlib.h>

int main()
{
    int pfd[2];
    int pid;
    char buff[256];

    ::pipe(pfd);
    ::sigignore(SIGCHLD);

    if ((pid = fork()) == 0) {
        /* child process */
        ::close(1);
        dup2(pfd[1], 1);
        ::close(pfd[0]);
        printf("test message.");
    } else {
        /* parent process */
        ::read(pfd[0], buff, 256);
        printf("buff = %s\n", buff);
    }
    
    return 0;
}
