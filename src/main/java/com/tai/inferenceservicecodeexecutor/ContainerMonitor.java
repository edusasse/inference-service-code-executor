package com.tai.inferenceservicecodeexecutor;

import com.rabbitmq.client.Channel;

import java.util.concurrent.*;

import redis.clients.jedis.*;

public class ContainerMonitor {
    private static final String HELLO_WORLD_QUEUE_NAME = "hello-world-queue";

    private Channel channel;
    private JedisPool jedisPool;

    public ContainerMonitor(Channel channel, JedisPool jedisPool) {
        System.out.println("Container Monitor started!");
        this.channel = channel;
        this.jedisPool = jedisPool;
    }

    public void scheduleQueueCheck() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Checking running containers!");

                // Stop the container associated with a specific image name
                String imageName = "rabbitmq-hello-world"; // This should be dynamically determined
                String containerId = null;
                String lastTimeStamp = null;
                try (Jedis jedis = jedisPool.getResource()) {
                    lastTimeStamp = jedis.get(imageName + RabbitMQDockerManager.LAST_RECEIVED_TIMESTAMP);
                    containerId = jedis.get(imageName);
                }
                if (lastTimeStamp != null) {
                    long lastMessagePlus60Sec = Long.parseLong(lastTimeStamp) + (60 * 1000);
                    System.out.println("Last timestamp [" + lastTimeStamp + "] last message + 60 seconds [" + lastMessagePlus60Sec + "] NOW [" + System.currentTimeMillis() + "] containerId [" + containerId + "]");
                    if ((lastMessagePlus60Sec < System.currentTimeMillis()) && containerId != null && !containerId.isEmpty()) {
                        System.out.println("Stoping container [" + containerId + "]");
                        Util.runProcessCommandAsync(new String[]{"docker", "stop", containerId});
                        // TODO double check the stop before removal
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.del(imageName + RabbitMQDockerManager.LAST_RECEIVED_TIMESTAMP);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 15, 30, TimeUnit.SECONDS);
    }

}
