/**
 * @file
 * @brief Lock manager for interprocess.
 *
 * Copyright (C) 2009,2010 Cybozu Inc., all rights reserved.
 *
 * @author Takashi HOSHINO <hoshino@labs.cybozu.co.jp>
 */

#include "ipc_lock_manager.hpp"

int main()
{
    IpcMessageQueue<SerializedLockRequest> mq(LOCK_MANAGER_MQ_NAME, 64);
    LockManagerServer lockMgr;
    
    SerializedLockRequest sReq;
    
    while (mq.get(sReq)) {

        LockRequestPtr reqP = LockRequestPtr(new LockRequest(sReq));

        if (VERBOSE) {
            std::cout << "Server: recv request: "
                      << reqP->toString() << std::endl;
        }
        
        lockMgr.processRequest(reqP);

        if (VERBOSE) { lockMgr.print(); }
    }

    /* mq is automatically closed. */
    
    return 0;
}
