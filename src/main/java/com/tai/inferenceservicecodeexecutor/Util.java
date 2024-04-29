package com.tai.inferenceservicecodeexecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class Util {
    public static String runProcessCommandAndGetOutput(String[] command) {
        StringBuilder output = new StringBuilder();
        try {
            System.out.println("Docker command [" + Arrays.toString(command) + "]");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);

            Process process = builder.start();
            try (InputStream inputStream = process.getInputStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.toString();
            } else {
                System.out.println("Command failed with exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void runProcessCommandAsync(String[] command) {
        // Create a new Thread object
        Thread dockerThread = new Thread(() -> {
            try {
                System.out.println("(Async) Docker command [" + Arrays.toString(command) + "]");
                final ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true); // Redirect error stream to input stream

                final Process process = builder.start();
                // Read the output from the Docker container
                try (InputStream inputStream = process.getInputStream();
                     final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                     final BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        System.out.println(line);
                    }
                }

                int exitCode = process.waitFor();
                System.out.println("Container exited with code " + exitCode);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        // Start the thread
        dockerThread.start();
    }

}
