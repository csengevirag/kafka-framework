package start;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        new newzookeeper();
        new newkafka();
        try {
            new newproducer("my-topic", 10000, 128, 40, 40.0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
