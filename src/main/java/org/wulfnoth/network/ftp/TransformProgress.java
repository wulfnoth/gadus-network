package org.wulfnoth.network.ftp;

public interface TransformProgress {

    long getCurrentValue();

    double getProgress();

    int getStatus();

}
