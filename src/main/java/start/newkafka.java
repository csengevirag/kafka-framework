package start;
import kafka.Kafka;
import kafka.server.KafkaServerStartable;
import org.slf4j.LoggerFactory;

import java.util.Properties;


public class newkafka {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(newkafka.class);
    public newkafka() throws InterruptedException {
        startInNewThread(() -> {
            try {
                String kafkaConfig = newkafka.class.getClassLoader().getResource("server.properties").getPath();
                logger.debug("Starting Kafka server using config:" + kafkaConfig);
                String[] kafkaArgs = {kafkaConfig};
                Properties serverProps = Kafka.getPropsFromArgs(kafkaArgs);
                KafkaServerStartable kafkaServerStartable = KafkaServerStartable.fromProps(serverProps);
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        kafkaServerStartable.shutdown();
                    }
                });
                kafkaServerStartable.startup();
            } catch (RuntimeException ex) {
                logger.error("Failed to start kafka", ex);
                throw ex;
            }
        }, "Kafka").join();
        logger.debug("Kafka started.");
    }

    private Thread startInNewThread(Runnable runnable, String kafka) {
        Thread thread = new Thread(runnable);
        thread.setName(kafka);
        thread.start();
        return thread;
    }



}
