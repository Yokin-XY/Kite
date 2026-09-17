package com.kite.app.foundation.devicebridge;

import android.os.ParcelFileDescriptor;

interface IKiteDeviceBridgeService {
    void destroy() = 16777114;

    int getProtocolVersion() = 1;

    int getServiceUid() = 2;

    int startProcess(
        String requestId,
        in String[] argv,
        in String[] environment,
        String workingDirectory,
        in ParcelFileDescriptor stdin,
        in ParcelFileDescriptor stdout,
        in ParcelFileDescriptor stderr,
        in ParcelFileDescriptor status
    ) = 3;

    boolean cancelProcess(String requestId) = 4;
}
