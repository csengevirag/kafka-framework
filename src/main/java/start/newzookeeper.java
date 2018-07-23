package start;

import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.server.quorum.QuorumPeerMain;
import org.slf4j.LoggerFactory;


public class newzookeeper {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(newzookeeper.class);
    //private static org.apache.logging.log4j.Logger logger = Logger.getLogger(newzookeeper.class.getName());
    public newzookeeper() {

       /* QuorumPeerMain.main("");
    }*/
        startInNewThread(() -> {
            try {
                String zookeeperConfig = newzookeeper.class.getClassLoader().getResource("zookeeper.properties").getPath();
                logger.debug("Starting Zookeeper server using config:" + zookeeperConfig);
                QuorumPeerMain.main(new String[]{zookeeperConfig});
            } catch (RuntimeException ex) {
                logger.error("Failed to start zookeeper", ex);
                throw ex;
            }
        }, "Zookeeper");
        logger.debug("Waiting until zookeper is started");
        new ZkClient("localhost:2181").waitUntilConnected();
        logger.debug("Zookeeper started");

    }

    private Thread startInNewThread(Runnable runnable, String zookeeper) {
        Thread thread = new Thread(runnable);
        thread.setName(zookeeper);
        thread.start();
        return thread;
    }
}
