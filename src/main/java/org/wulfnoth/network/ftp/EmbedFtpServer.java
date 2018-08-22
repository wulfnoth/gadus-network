package org.wulfnoth.network.ftp;

import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;

public class EmbedFtpServer {

    public static void main(String[] args) throws FtpException {
        FtpServerFactory serverFactory = new FtpServerFactory();

        ListenerFactory factory = new ListenerFactory();
        DataConnectionConfigurationFactory connectionFactory = new DataConnectionConfigurationFactory();
        connectionFactory.setActiveEnabled(false);
        
        BaseUser user = new BaseUser();
        user.setName("ycj");
        user.setPassword("cloud");
        user.setHomeDirectory("D:/");
        serverFactory.getUserManager().save(user);

        factory.setPort(2221);
        serverFactory.addListener("default", factory.createListener());
        FtpServer server = serverFactory.createServer();
        server.start();
    }

}
