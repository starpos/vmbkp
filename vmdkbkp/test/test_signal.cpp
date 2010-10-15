#include <stdio.h>
#include <signal.h>
#include <unistd.h>
#include <sys/wait.h>


int a;

void sig_int(int signo)
{
    a ++;
    ::printf("sig_int\n");
}

void sig_term(int signo)
{
    a ++;
    ::printf("sig_term\n");
}

int main()
{
    int pid = ::fork();
    if (pid == 0) {
        if (::signal(SIGTERM, sig_term) == SIG_ERR) {
            ::printf("SIGTERM error.\n");
        }
        if (::signal(SIGINT, sig_int) == SIG_ERR) {
            ::printf("SIGINT error.\n");
        }
        ::printf("child a = %d\n", a);
        ::sleep(1);
        ::printf("child a = %d\n", a);
        ::sleep(2);
        ::printf("child a = %d\n", a);
    } else {
        ::sleep(1);
        ::printf("send SIGTERM\n");
        ::kill(pid, SIGTERM);
        ::sleep(1);
        ::printf("send SIGINT\n");
        ::kill(pid, SIGINT);
        ::sleep(1);
        ::kill(pid, SIGKILL);
        int status;
        ::waitpid(pid, &status, 0);
    }
    
    return 0;
}
