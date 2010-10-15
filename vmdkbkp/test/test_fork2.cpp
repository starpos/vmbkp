#include <stdio.h>
#include <unistd.h>
#include <signal.h>
#include <assert.h>
#include <stdlib.h>

#include "vddk_manager.hpp"

void setupConfigData(ConfigData& cfg)
{
    /* Required by VddkManager and VmdkManager. */
    cfg.libDir = "/usr/local/lib/vmware-vix-disklib";
    cfg.configPath = "/usr/local/lib/vmware-vix-disklib/config";
    cfg.isRemote = true;
    cfg.vmMorefStr = "vm-3140";
    cfg.snapshotStr = "snapshot-3641";
    cfg.server = "vcenter";
    cfg.username = "testuser";
    cfg.password = "testpass";
    cfg.vmdkPath = "[MD3000i-APP1] vmbkptest-incr_1/vmbkptest-incr.vmdk";
}

void forkAndCallVddkManager(int* pfd, const ConfigData& cfg,
                            char *outfn, char *errfn)
{
    int pid;
    if ((pid = fork()) == 0) {
        /* child process */

        FILE *outfp = ::fopen(outfn, "w");
        FILE *errfp = ::fopen(errfn, "w");
        int outfd = ::fileno(outfp);
        int errfd = ::fileno(errfp);

        ::close(1);
        ::dup2(outfd, 1);
        ::close(2);
        ::dup2(errfd, 2);

        ::close(pfd[0]);
        FILE *ctlfp = ::fdopen(pfd[1], "w");
        
        {
            VddkManager vddkMgr = VddkManager(cfg, true, true);
            VmdkManager vmdkMgr = VmdkManager(vddkMgr);
        }
        ::fprintf(ctlfp, "finished");
        ::exit(0);
    } else {
        ::close(pfd[1]);
    }
}

int main()
{
    ConfigData cfg;
    setupConfigData(cfg);
    
    if (false) {
        int pfd1[2];
        int pfd2[2];
        char buff[256];

        int ret1 = ::pipe(pfd1);
        int ret2 = ::pipe(pfd2);
        ::printf("%d %d\n", ret1, ret2);
    
        ::sigignore(SIGCHLD);


        ::printf("call 1\n");
        forkAndCallVddkManager(pfd1, cfg, "test.out1", "test.err1");
        ::printf("call 1\n");
        ::memset(buff, 0, 256);
        ::read(pfd1[0], buff, 256);
        ::printf("buff: %s\n", buff);
    
        ::printf("call 2\n");
        forkAndCallVddkManager(pfd2, cfg, "test.out2", "test.err2");
        ::printf("call 2\n");
        ::memset(buff, 0, 256);
        ::read(pfd2[0], buff, 256);
        ::printf("buff: %s\n", buff);

    } else {
        {
            VddkManager vddkMgr = VddkManager(cfg, true, true);
            VmdkManager vmdkMgr = VmdkManager(vddkMgr);
        }
        {
            VddkManager vddkMgr = VddkManager(cfg, true, true);
            VmdkManager vmdkMgr = VmdkManager(vddkMgr);
        }
        
    }

    
    return 0;
}
