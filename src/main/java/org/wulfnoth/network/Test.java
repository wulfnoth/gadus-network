package org.wulfnoth.network;

import org.wulfnoth.network.ftp.ContinuousFtpClient;
import org.wulfnoth.network.ftp.TransformProgress;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class Test {

    private static String[] sizeUnits = {"B", "KB", "MB", "GB", "TB"};

    public static String changeSizeDisplay(long size) {
        double result = size;
        int unitIndex = 0;
        while (result >= 1024) {
            result /= 1024;
            unitIndex++;
        }
        return String.format("%.2f", result) + sizeUnits[unitIndex];
    }

    public static void main(String[] args) throws IOException {
        ContinuousFtpClient ftpClient = new ContinuousFtpClient();
        ftpClient.connect("localhost", 2221, "ycj", "cloud");
        final TransformProgress progress = ftpClient.download("JurassicWorld3DSBS1080pwww.newpct1.com.mkv", "D://asd.mkv");

        new Timer().schedule(new TimerTask() {

            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append(changeSizeDisplay(progress.getCurrentValue())).append("\t");
                sb.append(String.format("%.2f", progress.getProgress())).append("%");
                System.out.println(sb.toString());
            }
        }, 0, 2000);
    }

}
