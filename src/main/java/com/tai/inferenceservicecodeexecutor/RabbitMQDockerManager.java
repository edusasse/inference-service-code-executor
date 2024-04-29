package com.tai.inferenceservicecodeexecutor;

import com.rabbitmq.client.*;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class RabbitMQDockerManager {
    public static final String LAST_RECEIVED_TIMESTAMP = ":lastReceivedTimestamp";
    private Connection connection;
    private Channel channel;
    private JedisPool jedisPool;

    private static final String HELLO_WORLD_EXCHANGE_NAME = "hello-world-exchange";
    private static final String HELLO_WORLD_QUEUE_NAME = "hello-world-queue";

    private static final String EXECUTOR_QUEUE_NAME = "executor-service-queue";
    private static final String EXECUTOR_ALTERNATE_EXCHANGE_NAME = "executor-service-alternate-exchange";

    private static final String EXECUTOR_MONITOR_QUEUE_NAME = "executor-service-monitor-queue";
    private static final String EXECUTOR_MONITOR_EXCHANGE_NAME = "executor-service-monitor-exchange";

    public RabbitMQDockerManager() {
        System.out.println(" === RabbitMQDockerManager ===");
        this.jedisPool = new JedisPool(new JedisPoolConfig(), "redis", 6379);
    }

    public void start() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv("RABBITMQ_HOST"));
        factory.setPort(Integer.parseInt(System.getenv("RABBITMQ_PORT")));
        factory.setUsername(System.getenv("RABBITMQ_USER"));
        factory.setPassword(System.getenv("RABBITMQ_PASS"));

        // Configure your connection factory details here
        connection = factory.newConnection();
        channel = connection.createChannel();

        // Monitor
        ContainerMonitor monitor = new ContainerMonitor(channel, jedisPool);

        // =========================
        // Declare the alternate exchange
        // =========================
        System.out.println(" >>> Declaring exchange [" + EXECUTOR_ALTERNATE_EXCHANGE_NAME + "]");
        channel.exchangeDeclare(EXECUTOR_ALTERNATE_EXCHANGE_NAME, BuiltinExchangeType.FANOUT);

        // Declare the executor queue
        System.out.println(" >>> Declaring executor queue [" + EXECUTOR_QUEUE_NAME + "]");
        channel.queueDeclare(EXECUTOR_QUEUE_NAME, false, false, false, null);

        // Bind the alternate queue to the alternate exchange
        System.out.println(" >>> Bind the queue to its exchange [" + EXECUTOR_QUEUE_NAME + "] -> [" + EXECUTOR_ALTERNATE_EXCHANGE_NAME + "]");
        channel.queueBind(EXECUTOR_QUEUE_NAME, EXECUTOR_ALTERNATE_EXCHANGE_NAME, "");

        // =========================
        // Declare the monitor exchange
        // =========================
        System.out.println(" >>> Declaring monitor exchange [" + EXECUTOR_MONITOR_EXCHANGE_NAME + "]");
        channel.exchangeDeclare(EXECUTOR_MONITOR_EXCHANGE_NAME, BuiltinExchangeType.FANOUT);

        // Declare the executor queue
        System.out.println(" >>> Declaring executor monitor queue [" + EXECUTOR_MONITOR_QUEUE_NAME + "]");
        channel.queueDeclare(EXECUTOR_MONITOR_QUEUE_NAME, false, false, false, null);

        // Bind the alternate queue to the alternate exchange
        System.out.println(" >>> Bind the queue to its exchange [" + EXECUTOR_MONITOR_QUEUE_NAME + "] -> [" + EXECUTOR_MONITOR_EXCHANGE_NAME + "]");
        channel.queueBind(EXECUTOR_MONITOR_QUEUE_NAME, EXECUTOR_MONITOR_EXCHANGE_NAME, "");

        // =========================
        // Declare the hello-world exchange
        // =========================
        System.out.println(" >>> Declaring exchange [" + HELLO_WORLD_EXCHANGE_NAME + "] with AE [" + EXECUTOR_ALTERNATE_EXCHANGE_NAME + "]");
        Map<String, Object> args = new HashMap<>();
        args.put("alternate-exchange", EXECUTOR_ALTERNATE_EXCHANGE_NAME);
        channel.exchangeDeclare(HELLO_WORLD_EXCHANGE_NAME, BuiltinExchangeType.DIRECT, false, false, args);

        // Declare the queue for the hello world app, in the future this needs to be made when a new app is added
        System.out.println(" >>> Declaring helle-world queue [" + HELLO_WORLD_QUEUE_NAME + "]");
        channel.queueDeclare(HELLO_WORLD_QUEUE_NAME, false, false, false, null);

        // =========================
        // CONSUMERS
        // =========================
        System.out.println(" >>> Connecting to queue [" + EXECUTOR_QUEUE_NAME + "]");
        channel.basicConsume(EXECUTOR_QUEUE_NAME, true, deliverCallback, consumerTag -> {
        });

        System.out.println(" >>> Connecting to queue [" + EXECUTOR_MONITOR_QUEUE_NAME + "]");
        channel.basicConsume(EXECUTOR_MONITOR_QUEUE_NAME, true, deliverCallbackMonitor, consumerTag -> {
        });

        monitor.scheduleQueueCheck();
    }

    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        System.out.println(" >>> Received[" + EXECUTOR_QUEUE_NAME + "]: " + message + " [consumerTag]: " + consumerTag + " [delivery]: " + delivery.getEnvelope().toString());

        try {
            handleDockerImage(message);
            // TODO return message to the queue
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    };

    DeliverCallback deliverCallbackMonitor = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        System.out.println(" >>> Received[" + EXECUTOR_MONITOR_QUEUE_NAME + "]: " + message + " [consumerTag]: " + consumerTag + " [delivery]: " + delivery.getEnvelope().toString());

        // Assuming that the image name is parsed from the message (json or another format)
        String imageName = parseImageNameFromJson(delivery.getEnvelope().getExchange());

        // Create a unique Redis key by combining the exchange name and the image name
        String redisKey = imageName + LAST_RECEIVED_TIMESTAMP;

        // Get the current timestamp
        long timestamp = System.currentTimeMillis();

        // Store the timestamp in Redis
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(redisKey, String.valueOf(timestamp));
        }
    };

    public Channel getChannel() {
        return channel;
    }

    private void handleDockerImage(String jsonMessage) {
        // Parse JSON to get Docker image name
        String imageName = parseImageNameFromJson(jsonMessage);

        // Run Docker command
        synchronized (imageName) {
            runDockerContainer(imageName);
        }
    }

    private void handleDockerImageStats(String jsonMessage) {
        // Parse JSON to get Docker image name
        String imageName = parseImageNameFromJson(jsonMessage);

        // Run Docker command
        runDockerStats(imageName);
    }

    private String parseImageNameFromJson(String json) {
        return "rabbitmq-hello-world";
    }

    private void runDockerContainer(String imageName) {
        String containerId = null;
        try (Jedis jedis = jedisPool.getResource()) {
            containerId = jedis.get(imageName);
        }
        if (containerId != null && !containerId.isEmpty()) {
            // Check if the container is running or stopped, and start it if stopped
            String[] dockerInspectCmd = { "docker", "inspect", "--format='{{.State.Running}}'", containerId };
            String isRunning = Util.runProcessCommandAndGetOutput(dockerInspectCmd);
            System.out.println("Container [" + containerId + "] Status [" + isRunning + "]");
            if (isRunning == null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    System.out.println("Container [" + containerId + "] removing image containerId reference!");
                    jedis.del(imageName);
                    runDockerContainer(imageName);
                }
            } else if ("false".equals(isRunning.trim()) || "'false'".equals(isRunning.trim())) {
                String[] dockerStartCmd = new String[]{ "docker", "start", containerId};
                Util.runProcessCommandAndGetOutput(dockerStartCmd);
            } else {
                System.out.println("Container [" + containerId + "] is in status [" + isRunning + "]");
            }
        } else {
            final String[] dockerRunCmd = {
                    "docker", "run", "-d",
                    "-v", "/var/run/docker.sock:/var/run/docker.sock",
                    "-e", "RABBITMQ_HOST=rabbitmq",
                    "-e", "RABBITMQ_PORT=5672",
                    "-e", "RABBITMQ_USER=guest",
                    "-e", "RABBITMQ_PASS=guest",
                    "--network=t-aiinferenceservicecodeexecutor_internal_network", // internal network only
                    "--user=1000:1000", // Running as Non-Privileged User
                    "--cpus=1", // Limit resources
                    "--cpus", "0.25",
                    "--memory", "512m", imageName};

            containerId = Util.runProcessCommandAndGetOutput(dockerRunCmd);

            System.out.println("containerId [" + containerId + "]");

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set(imageName, containerId);
            }
        }
    }

    private void runDockerStats(String imageName) {
        final String[] dockerRunCmd = {
                "docker", "stats"};

        System.out.println("Docker command [" + Arrays.toString(dockerRunCmd) + "]");

        Util.runProcessCommandAsync(dockerRunCmd);
    }


    public void stop() throws IOException, TimeoutException {
        channel.close();
        connection.close();
    }

    public static void main(String[] args) throws Exception {
        RabbitMQDockerManager manager = new RabbitMQDockerManager();
        manager.start();
        // Add shutdown hook to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                manager.stop();
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }));
    }
}
