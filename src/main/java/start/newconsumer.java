package start;

import java.util.Properties;
import java.util.Arrays;

import kafka.Kafka;
import kafka.server.KafkaServerStartable;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.LoggerFactory;


public class newconsumer {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(newconsumer.class);
    public newconsumer() throws InterruptedException {
        startInNewThread(() -> {
            try {
                String consumerConfig = newconsumer.class.getClassLoader().getResource("consumer.properties").getPath();
                logger.debug("Starting consumer group config:" + consumerConfig);
                String[] consumerArgs = {consumerConfig};
                Properties consumerProps = Kafka.getPropsFromArgs(consumerArgs);
                KafkaServerStartable kafkaServerStartable = KafkaServerStartable.fromProps(consumerProps);
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



        /*public static void main(String[] args) throws Exception {
            if(args.length < 2){
                System.out.println("Usage: consumer <topic> <groupname>");
                return;
            }

            String topic = args[0].toString();
            String group = args[1].toString();
            Properties props = new Properties();
            props.put("bootstrap.servers", "localhost:9092");
            props.put("group.id", group);
            props.put("enable.auto.commit", "true");
            props.put("auto.commit.interval.ms", "1000");
            props.put("session.timeout.ms", "30000");
            props.put("key.deserializer",
                    "org.apache.kafka.common.serializa-tion.StringDeserializer");
            props.put("value.deserializer",
                    "org.apache.kafka.common.serializa-tion.StringDeserializer");
            KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);

            consumer.subscribe(Arrays.asList(topic));
            System.out.println("Subscribed to topic " + topic);
            int i = 0;

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                for (ConsumerRecord<String, String> record : records)
                    System.out.printf("offset = %d, key = %s, value = %s\n",
                            record.offset(), record.key(), record.value());
            }
        }*/
    }

