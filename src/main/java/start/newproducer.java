/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package start;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.utils.Utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.Math.log;

public class newproducer {

    public newproducer(String topicname_, int numrecords_, int recordsize_, int throughput_, double lambda_) throws InterruptedException, IOException {

            /* parse args */
            String topicName = topicname_;
            int numRecords = numrecords_;
            int recordSize = recordsize_;
            int throughput = throughput_;
            double lambda = lambda_;
            List<String> producerProps = null;
            String producerConfig = newproducer.class.getClassLoader().getResource("producer.properties").getPath();
            String payloadFilePath = null;
            String transactionalId = "performance-producer-default-transactional-id";
            boolean shouldPrintMetrics = false;
            long transactionDurationMs = 0L;
            boolean transactionsEnabled =  false;

            // since default value gets printed with the help text, we are escaping \n there and replacing it with correct value here.
            String payloadDelimiter = "\\n";


            List<byte[]> payloadByteList = new ArrayList<>();
            if (payloadFilePath != null) {
                Path path = Paths.get(payloadFilePath);
                System.out.println("Reading payloads from: " + path.toAbsolutePath());
                if (Files.notExists(path) || Files.size(path) == 0)  {
                    throw new  IllegalArgumentException("File does not exist or empty file provided.");
                }

                String[] payloadList = new String(Files.readAllBytes(path), "UTF-8").split(payloadDelimiter);

                System.out.println("Number of messages read: " + payloadList.length);

                for (String payload : payloadList) {
                    payloadByteList.add(payload.getBytes(StandardCharsets.UTF_8));
                }
            }

            Properties props = new Properties();
            if (producerConfig != null) {
                props.putAll(Utils.loadProps(producerConfig));
            }
            if (producerProps != null)
                for (String prop : producerProps) {
                    String[] pieces = prop.split("=");
                    if (pieces.length != 2)
                        throw new IllegalArgumentException("Invalid property: " + prop);
                    props.put(pieces[0], pieces[1]);
                }

            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
            System.out.println("transaction enabled: " + transactionsEnabled);
            if (transactionsEnabled)
                props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);

            KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(props);

            if (transactionsEnabled)
                producer.initTransactions();

            /* setup perf test */
            byte[] payload = null;
            Random random = new Random(0);
            if (recordSize != 0) {
                payload = new byte[recordSize];
                for (int i = 0; i < payload.length; ++i)
                    payload[i] = (byte) (random.nextInt(26) + 65);
            }
            ProducerRecord<byte[], byte[]> record;
            Stats stats = new Stats(numRecords, 5000);
            long startMs = System.currentTimeMillis();

            ThroughputThrottler throttler = new ThroughputThrottler(throughput, startMs);

            int currentTransactionSize = 0;
            long transactionStartTime = 0;
            // ide irj!!
            for (int i = 0; i < numRecords; i++) {
                Random rand = new Random();
                double u = rand.nextDouble();
                double t;
                t = (log(u)) / (-1.0) * lambda; //lambda ms
                long x = (long) t;
                if (x >= 30000)
                    x = 30000;
                try {
                    Thread.sleep(x);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                if (transactionsEnabled && currentTransactionSize == 0) {
                    producer.beginTransaction();
                    transactionStartTime = System.currentTimeMillis();
                }


                if (payloadFilePath != null) {
                    payload = payloadByteList.get(random.nextInt(payloadByteList.size()));
                }
                record = new ProducerRecord<>(topicName, payload);

                long sendStartMs = System.currentTimeMillis();
                Callback cb = stats.nextCompletion(sendStartMs, payload.length, stats);
                producer.send(record, cb);

                currentTransactionSize++;
                if (transactionsEnabled && transactionDurationMs <= (sendStartMs - transactionStartTime)) {
                    producer.commitTransaction();
                    currentTransactionSize = 0;
                }

                if (throttler.shouldThrottle(i, sendStartMs)) {
                    throttler.throttle();
                }
            }

            if (transactionsEnabled && currentTransactionSize != 0)
                producer.commitTransaction();

            if (!shouldPrintMetrics) {
                producer.close();

                /* print final results */
                stats.printTotal();
            } else {
                // Make sure all messages are sent before printing out the stats and the metrics
                // We need to do this in a different branch for now since tests/kafkatest/sanity_checks/test_performance_services.py
                // expects this class to work with older versions of the client jar that don't support flush().
                producer.flush();

                /* print final results */
                stats.printTotal();

                /* print out metrics */
                ToolsUtils.printMetrics(producer.metrics());
                producer.close();
            }
    }

    private static class Stats {
        private long start;
        private long windowStart;
        private int[] latencies;
        private int[] allLatencies;
        private int sampling;
        private int iteration;
        private int index;
        private long count;
        private long bytes;
        private int maxLatency;
        private long totalLatency;
        private long windowCount;
        private int windowMaxLatency;
        private int windowMinLatency;
        private long windowTotalLatency;
        private long windowBytes;
        private long reportingInterval;

        public Stats(long numRecords, int reportingInterval) {
            this.start = System.currentTimeMillis();
            this.windowStart = System.currentTimeMillis();
            this.index = 0;
            this.iteration = 0;
            this.sampling = (int) (numRecords / Math.min(numRecords, 500000));
            this.allLatencies = new int[(int) numRecords];
            this.latencies = new int[(int) (numRecords / this.sampling) + 1];
            this.index = 0;
            this.maxLatency = 0;
            this.totalLatency = 0;
            this.windowCount = 0;
            this.windowMaxLatency = 0;
            this.windowMinLatency = 0;
            this.windowTotalLatency = 0;
            this.windowBytes = 0;
            this.totalLatency = 0;
            this.reportingInterval = reportingInterval;
        }

        public void record(int iter, int latency, int bytes, long time) {
            this.allLatencies[(int) count]=latency;
            this.count++;
            this.bytes += bytes;
            this.totalLatency += latency;
            this.maxLatency = Math.max(this.maxLatency, latency);
            this.windowCount++;
            this.windowBytes += bytes;
            this.windowTotalLatency += latency;
            this.windowMaxLatency = Math.max(windowMaxLatency, latency);
            this.windowMinLatency = Math.min(windowMinLatency, latency);
            if (iter % this.sampling == 0) {
                this.latencies[index] = latency;
                this.index++;
            }
            /* maybe report the recent perf */
            if (time - windowStart >= reportingInterval) {
                printWindow();
                newWindow();
            }
        }

        public Callback nextCompletion(long start, int bytes, Stats stats) {
            Callback cb = new PerfCallback(this.iteration, start, bytes, stats);
            this.iteration++;
            return cb;
        }

        public void printWindow() {
            long ellapsed = System.currentTimeMillis() - windowStart;
            double recsPerSec = 1000.0 * windowCount / (double) ellapsed;
            double mbPerSec = 1000.0 * this.windowBytes / (double) ellapsed / (1024.0 * 1024.0);
            System.out.printf("%d records sent, %.1f records/sec (%.2f MB/sec), %.1f ms avg latency, %.1f max latency.%n",
                    windowCount,
                    recsPerSec,
                    mbPerSec,
                    windowTotalLatency / (double) windowCount,
                    (double) windowMaxLatency, (double) windowMinLatency);
            String newline = System.getProperty("line.separator");
            FileWriter fw = null;
            try {
                fw = new FileWriter("test.txt", true);
                PrintWriter pw = new PrintWriter(fw);
                pw.println(windowCount +", " +
                        recsPerSec +", " + mbPerSec + ", " +
                        windowTotalLatency / (double) windowCount + ", " +
                        (double) windowMaxLatency + ", ");
                pw.close();
                fw.close();
                System.out.println("alma");
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        public void newWindow() {
            this.windowStart = System.currentTimeMillis();
            this.windowCount = 0;
            this.windowMaxLatency = 0;
            this.windowTotalLatency = 0;
            this.windowBytes = 0;
        }

        public void printTotal() {
            long elapsed = System.currentTimeMillis() - start;
            double recsPerSec = 1000.0 * count / (double) elapsed;
            double mbPerSec = 1000.0 * this.bytes / (double) elapsed / (1024.0 * 1024.0);
            int[] percs = percentiles(this.latencies, index, 0.5, 0.95, 0.99, 0.999);
            FileWriter fw = null;
            try {
                fw = new FileWriter("test.txt", true);
                PrintWriter pw = new PrintWriter(fw);
                pw.println(count +" records sent," + recsPerSec +" records/sec (" + mbPerSec + " MB/sec)," + totalLatency / (double) count + " ms avg latency," +
                        (double) maxLatency + " ms max latency," + percs[0] + " ms 50th," + percs[1] + " ms 95th," + percs[2] + " ms 99th," +
                        percs[3] + " ms 99.9th.%n");
                pw.close();
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            FileWriter mw = null;
            try {
                mw = new FileWriter("block.txt", true);
                PrintWriter dw = new PrintWriter(mw);
                for(int r = 0; r < allLatencies.length; r++) {
                    dw.println(allLatencies[r]);
                }
                dw.close();
                mw.close();
                System.out.println("körte");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static int[] percentiles(int[] latencies, int count, double... percentiles) {
            int size = Math.min(count, latencies.length);
            Arrays.sort(latencies, 0, size);
            int[] values = new int[percentiles.length];
            for (int i = 0; i < percentiles.length; i++) {
                int index = (int) (percentiles[i] * size);
                values[i] = latencies[index];
            }
            return values;
        }
    }

    private static final class PerfCallback implements Callback {
        private final long start;
        private final int iteration;
        private final int bytes;
        private final Stats stats;

        public PerfCallback(int iter, long start, int bytes, Stats stats) {
            this.start = start;
            this.stats = stats;
            this.iteration = iter;
            this.bytes = bytes;
        }

        public void onCompletion(RecordMetadata metadata, Exception exception) {
            long now = System.currentTimeMillis();
            int latency = (int) (now - start);
            this.stats.record(iteration, latency, bytes, now);
            if (exception != null)
                exception.printStackTrace();
        }
    }

}
