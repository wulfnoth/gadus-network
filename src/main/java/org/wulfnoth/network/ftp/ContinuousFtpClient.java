package org.wulfnoth.network.ftp;

import io.netty.util.CharsetUtil;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class ContinuousFtpClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ContinuousFtpClient.class);

    public static final int CONNECT_SUCCESS = 230;
    public static final int USER_INFO_ERROR = 530;
    public static final int CONNECT_REPLAY_ERROR = 623;
    public static final int NEVER_CONNECT = 625;

    private int connectionCode = NEVER_CONNECT;

    public FTPClient ftpClient;

    public int getConnectionCode() {
        return connectionCode;
    }

    public ContinuousFtpClient() {
        ftpClient = new FTPClient();
    }

    public boolean connect(String hostname, int port, String username, String password) throws IOException {
        ftpClient.connect(hostname, port);
        ftpClient.setControlEncoding("UTF-8");
        if(FTPReply.isPositiveCompletion(ftpClient.getReplyCode())){
            if (ftpClient.login(username, password)) {
                connectionCode = CONNECT_SUCCESS;
                return true;
            } else {
                connectionCode = USER_INFO_ERROR;
            }
        }
        close();
        return false;
    }

    public List<FTPFile> getRemoteFilePath() throws IOException {
        return Arrays.asList(ftpClient.listFiles());
    }

    /**
     * 从FTP服务器上下载文件,支持断点续传，上传百分比汇报
     * @param remote 远程文件路径
     * @param local 本地文件路径
     * @return 上传的状态
     * @throws IOException IOException
     */
    public DownloadStatus download(String remote,String local) throws IOException{
        //设置被动模式
        ftpClient.enterLocalPassiveMode();
        //设置以二进制方式传输
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        DownloadStatus result;

        //检查远程文件是否存在
        FTPFile[] files = ftpClient.listFiles(remote);
        if(files.length != 1){
            return DownloadStatus.Remote_File_NOTExist;
        }

        long lRemoteSize = files[0].getSize();
        File f = new File(local);
        //本地存在文件，进行断点下载
        if(f.exists()){
            long localSize = f.length();
            //判断本地文件大小是否大于远程文件大小
            if(localSize >= lRemoteSize){
                //System.out.println("本地文件大于远程文件，下载中止");
                return DownloadStatus.Local_Bigger_Remote;
            }

            //进行断点续传，并记录状态
            FileOutputStream out = new FileOutputStream(f,true);
            ftpClient.setRestartOffset(localSize);
            InputStream in = ftpClient.retrieveFileStream(remote);
            byte[] bytes = new byte[1024];
            long step = lRemoteSize /100;
            long process=localSize /step;
            int c;
            while((c = in.read(bytes))!= -1){
                out.write(bytes,0,c);
                localSize+=c;
                long nowProcess = localSize /step;
                if(nowProcess > process){
                    process = nowProcess;
                }
            }
            in.close();
            out.close();
            boolean isDo = ftpClient.completePendingCommand();
            if(isDo){
                result = DownloadStatus.Download_From_Break_Success;
            }else {
                result = DownloadStatus.Download_From_Break_Failed;
            }
        }else {
            OutputStream out = new FileOutputStream(f);
            InputStream in= ftpClient.retrieveFileStream(new String(remote.getBytes("GBK"),CharsetUtil.ISO_8859_1));
            byte[] bytes = new byte[1024];
            long step = lRemoteSize /100;
            long process=0;
            long localSize = 0L;
            int c;
            while((c = in.read(bytes))!= -1){
                out.write(bytes, 0, c);
                localSize+=c;
                long nowProcess = localSize /step;
                if(nowProcess > process){
                    process = nowProcess;
//                    if(process % 10 == 0)
                        //System.out.println("下载进度："+process);
                }
            }
            in.close();
            out.close();
            boolean upNewStatus = ftpClient.completePendingCommand();
            if(upNewStatus){
                result = DownloadStatus.Download_New_Success;
            }else {
                result = DownloadStatus.Download_New_Failed;
            }
        }
        return result;
    }

    /**
     * 递归创建远程服务器目录
     * @param remote 远程服务器文件绝对路径
     * @param ftpClient FTPClient对象
     * @return 目录创建是否成功
     * @throws IOException IOException
     */
    private UploadStatus createDirectory(String remote,FTPClient ftpClient) throws IOException{
        UploadStatus status = UploadStatus.Create_Directory_Success;
        String directory = remote.substring(0,remote.lastIndexOf("/")+1);
        if(!directory.equalsIgnoreCase("/")&&!ftpClient.changeWorkingDirectory(directory)){
            //如果远程目录不存在，则递归创建远程服务器目录
            int start = 0;
            int end = directory.indexOf("/",start);
            if(directory.startsWith("/")){
                start = 1;
            }
            do {
                String subDirectory = remote.substring(start, end);
                if (!ftpClient.changeWorkingDirectory(subDirectory)) {
                    if (ftpClient.makeDirectory(subDirectory)) {
                        ftpClient.changeWorkingDirectory(subDirectory);
                    } else {
                        //System.out.println("创建目录失败");
                        return UploadStatus.Create_Directory_Fail;
                    }
                }

                start = end + 1;
                end = directory.indexOf("/", start);

                //检查所有目录是否创建完毕
            } while (end > start);
        }
        return status;
    }

    /**
     * 上传文件到服务器,新上传和断点续传
     * @param remoteFile 远程文件名，在上传之前已经将服务器工作目录做了改变
     * @param localFile 本地文件File句柄，绝对路径
     * @param ftpClient FTPClient引用
     * @return 上传结果
     * @throws IOException IOException
     */
    private UploadStatus uploadFile(String remoteFile,File localFile,FTPClient ftpClient,long remoteSize) throws IOException{
        UploadStatus status;
        //显示进度的上传
        long step = localFile.length() / 100;
        long process = 0;
        long readBytesLength = 0L;
        RandomAccessFile raf = new RandomAccessFile(localFile,"r");
        OutputStream out = ftpClient.appendFileStream(remoteFile);
        //断点续传
        if(remoteSize>0){
            ftpClient.setRestartOffset(remoteSize);
            process = remoteSize /step;
            raf.seek(remoteSize);
            readBytesLength = remoteSize;
        }
        byte[] bytes = new byte[1024];
        int c;
        while((c = raf.read(bytes))!= -1){
            out.write(bytes,0,c);
            readBytesLength+=c;
            if(readBytesLength / step != process){
                process = readBytesLength / step;
                //System.out.println("上传进度:" + process);
            }
        }
        out.flush();
        raf.close();
        out.close();
        boolean result =ftpClient.completePendingCommand();
        if(remoteSize > 0){
            status = result?UploadStatus.Upload_From_Break_Success:UploadStatus.Upload_From_Break_Failed;
        }else {
            status = result?UploadStatus.Upload_New_File_Success:UploadStatus.Upload_New_File_Failed;
        }
        return status;
    }

    @Override
    public void close() throws IOException {
        if(ftpClient.isConnected()){
            ftpClient.disconnect();
        }
    }

    public static void main(String[] args) {
        try (ContinuousFtpClient ftpClient = new ContinuousFtpClient()) {
            if (!ftpClient.connect("localhost", 2221, "ycj", "cloud"))
                System.out.println(ftpClient.getConnectionCode());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
