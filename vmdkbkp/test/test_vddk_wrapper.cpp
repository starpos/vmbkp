#include "vddk_wrapper.hpp"

bool isDump__, isRestore__;

void setupConfigData(ConfigData& cfg)
{
    /* Required by VddkManager and VmdkManager. */
    cfg.libDir = "/usr/local/lib/vmware-vix-disklib";
    cfg.configPath = "/usr/local/lib/vmware-vix-disklib/config";
    cfg.isRemote = true;
    cfg.vmMorefStr = "vm-4200";
    cfg.server = "vcenter";
    cfg.username = "testuser";
    cfg.password = "testpass";

    if (isDump__) {
        cfg.snapshotStr = "snapshot-4205";
        cfg.vmdkPath = "[MD3000i-APP1] vmbkptest-incr2/vmbkptest-incr2.vmdk";
    }
    if (isRestore__) {
        cfg.snapshotStr = "snapshot-4205";
        cfg.vmdkPath = "[MD3000i-APP1] vmbkptest-incr2/vmbkptest-incr2_2.vmdk";
    }
}

int main2()
{
    isDump__ = false;
    isRestore__ = true;
    
    ConfigData cfg;
    setupConfigData(cfg);

    try {
        VmdkDumpHeader dumpH;
        dumpH.initialize
            (128, cfg.blocksize, VIXDISKLIB_ADAPTER_SCSI_LSILOGIC);
        VddkManager vddkMgr(cfg, true, false);
        vddkMgr.createVmdkFile(dumpH);
    } catch (const VixException& e) {
        e.writeLog();
    }
    
    return 0;
}

int main()
{
    isDump__ = false;
    isRestore__ = true;
    
    ConfigData cfg;
    setupConfigData(cfg);

    //VddkController vddkCtrl(cfg, true, true);
    //vddkCtrl.reset(true, true);
    //vddkCtrl.reset(true, true);
    //vddkCtrl.kill(SIGTERM);
    //::sleep(10);
    //vddkCtrl.kill(SIGINT);

    if (isDump__) { /* test methods for dump. */

        VddkController vddkCtrl(cfg, true, true);
        vddkCtrl.start();
        vddkCtrl.open();
    
        std::string transportMode = vddkCtrl.getTransportMode();
        ::printf("PARENT: %s\n", transportMode.c_str());

        VmdkInfo vmdkInfo;
        vddkCtrl.readVmdkInfo(vmdkInfo);
        ::printf("PARENT: %s\n", vmdkInfo.toString().c_str());

        StringMap strMap;
        vddkCtrl.readMetadata(strMap);
        std::stringstream ss;
        put(strMap, ss);
        ::printf("PARENT: %s\n", ss.str().c_str());

        uint8 buf[cfg.blocksize];
        ::memset(buf, 0, cfg.blocksize);
        vddkCtrl.readBlock(0, buf);
        for (size_t i = 0; i < cfg.blocksize; i ++) {
            if (i % 16 == 0) {::printf("%zu: ", i);}
            int ch = static_cast<int>(buf[i]);
            ::printf("%02x ", ch);
            if (i % 16 == 15) {::printf("\n");}
        }
        ::printf("\n");
        vddkCtrl.stop();
    }

    if (isRestore__) { /* test methods for restore. */

        VddkController vddkCtrl(cfg, false, false);
        vddkCtrl.start();
        
        uint8 zeroBlock[cfg.blocksize];
        ::memset(zeroBlock, 0, cfg.blocksize);
        uint8 oneBlock[cfg.blocksize];
        ::memset(oneBlock, 1, cfg.blocksize);
        
        /* allocate blocks */
        //vddkCtrl.reset(false, false);

        /*
        VmdkDumpHeader dumpH;
        dumpH.initialize
            (128, cfg.blocksize, VIXDISKLIB_ADAPTER_SCSI_LSILOGIC);
        vddkCtrl.createVmdkFile(dumpH);
        */
        
        vddkCtrl.open();

        for (int i = 0; i < 32; i ++) {
            vddkCtrl.writeBlock(i, zeroBlock);
        }
        
        StringMap strMap;
        strMap["test_key"] = "test_value";
        vddkCtrl.writeMetadata(strMap);

        /* restore blocks */
        ::printf("reset connection begin.\n");
        vddkCtrl.reset(false, true);
        ::printf("reset connection end.\n");
        vddkCtrl.open();

        for (int i = 0; i < 32; i ++) {
            vddkCtrl.writeBlock(i, oneBlock);
        }

        ::printf("test end\n");
        vddkCtrl.stop();
    }
    
    return 0;
}
