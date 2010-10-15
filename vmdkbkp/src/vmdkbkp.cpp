/**
 * @file
 * @brief Backup software of vmdk file for VMware vSphere environment.
 *
 * Description:
 *   This command-line tool enables backup/restore/manage
 *   disk image file normally suffixed with '.vmdk' from VMWare, Inc..
 *   It supports not only local vmdk file but also remote vmdk file
 *   managed by vCenter server (or maybe esx/esxi host).
 *  
 * Features:
 *   * Support per-VM backup.
 *   * Support multigeneration backup support
 *     with space-efficient binary diff.
 *   * Support data transfer via SAN network
 *     using vStorage APIs for Data Protection (VADP)
 *     with VDDK (Virtual Disk Development Kit by VMware).
 *   * Support on-line backup using a snapshot with VDDK.
 *   * Support SPARSE vmdk file to utilize storage capacity
 *     without having all-zero data blocks in backup files.
 * 
 * Build:
 *   * Required libraries: openssl, VDDK.
 *   * Compiler for test: gcc-4.3.4
 *     You need LDFLAGS with '-lvixDiskLib' and '-lcrypto'.
 *   * Environment for test: CentOS 5.3 (Linux kernel 2.6.18-128.1.6.el5)
 *
 * Usage:
 *   See --help option.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 * 
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */
#include <stdlib.h>
#include <unistd.h>
#include <time.h>
#include "command.hpp"

int main(int argc, char * const argv[])
{
    Command cmd;
    bool ret1, ret2;

    ::srand(::time(0) + ::getpid());
    
    ret1 = cmd.parseCommandlineOptions(argc, argv);
    if (! ret1) { 
        return -1;        
    }

    ret2 = cmd.run();
    if (! ret2) {
        std::cerr << "Failed cmd.run().\n";
        return -1;
    }

    /* success */
    return 0;
}

/* end of file */
