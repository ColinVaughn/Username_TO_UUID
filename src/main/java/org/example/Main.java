package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import com.google.gson.*;

public class Main {
    private static final String USERNAME_FILE_URL = "https://gist.githubusercontent.com/ColinVaughn/e828dd1c16e6cd48b0c178873c182b83/raw/test.txt";
    private static final String UUID_FILE = "uuids.json";
    private static JTextArea outputTextArea;

    public static void main(String[] args) {
        // Create and configure the GUI frame
        JFrame frame = new JFrame("Username to UUID Converter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        outputTextArea = new JTextArea();
        outputTextArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputTextArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        JButton convertButton = new JButton("Convert");
        convertButton.addActionListener(e -> convertUsernames());
        frame.add(convertButton, BorderLayout.SOUTH);

        JTextField inputField = new JTextField();
        frame.add(inputField, BorderLayout.NORTH);

        JButton submitButton = new JButton("Submit");
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = inputField.getText();
                if (input != null && !input.trim().isEmpty()) {
                    outputTextArea.append("Manually entered usernames: " + input + "\n");
                    String[] usernames = input.split("\\s*,\\s*");
                    convertUsernames(Arrays.asList(usernames));
                }
            }
        });
        frame.add(submitButton, BorderLayout.EAST);

        frame.setSize(400, 300);
        frame.setVisible(true);
    }

    private static void convertUsernames() {
        List<String> usernames = readUsernames();
        convertUsernames(usernames);
    }

    private static void convertUsernames(List<String> usernames) {
        if (usernames.isEmpty()) {
            outputTextArea.append("No usernames provided. Exiting...\n");
            return;
        }

        Map<String, UserInfo> uuidMap = readUUIDs();
        for (String usernameWithType : usernames) {
            String[] parts = usernameWithType.split("-");
            String username = parts[0].trim();
            String type = parts.length > 1 ? parts[1].trim() : null;
            if (type == null || (!type.equalsIgnoreCase("Premium") && !type.equalsIgnoreCase("Basic"))) {
                outputTextArea.append("Invalid type for username: " + username + "\n");
                continue;
            }
            UserInfo userInfo = uuidMap.get(username);
            if (userInfo == null) {
                userInfo = convertUsernameToUserInfo(username);
                if (userInfo != null) {
                    uuidMap.put(username, userInfo);
                } else {
                    outputTextArea.append("Failed to fetch UUID for username: " + username + "\n");
                    continue;
                }
            }
            userInfo.setType(type);

            outputTextArea.append("Minecraft username: " + username + "\n");
            outputTextArea.append("UUID: " + userInfo.getUuid() + "\n");
            outputTextArea.append("User type: " + userInfo.getType() + "\n");
            outputTextArea.append("\n");
        }

        saveUUIDs(uuidMap);
    }

    private static List<String> readUsernames() {
        List<String> usernames = new ArrayList<>();
        try {
            URL url = new URL(USERNAME_FILE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    usernames.add(line.trim());
                }
                reader.close();
            }
        } catch (IOException e) {
            outputTextArea.append("Failed to read usernames from URL.\n");
        }
        return usernames;
    }

    private static Map<String, UserInfo> readUUIDs() {
        Map<String, UserInfo> uuidMap = new HashMap<>();
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(UUID_FILE)));
            JsonObject jsonObject = new JsonParser().parse(jsonContent).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String username = entry.getKey();
                JsonObject userObject = entry.getValue().getAsJsonObject();
                String uuid = userObject.get("uuid").getAsString();
                String type = userObject.get("type").getAsString();
                UserInfo userInfo = new UserInfo(uuid, type);
                uuidMap.put(username, userInfo);
            }
        } catch (IOException e) {
            return uuidMap;
        }
        return uuidMap;
    }

    private static void saveUUIDs(Map<String, UserInfo> uuidMap) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, UserInfo> entry : uuidMap.entrySet()) {
            String username = entry.getKey();
            UserInfo userInfo = entry.getValue();
            JsonObject userObject = new JsonObject();
            userObject.addProperty("uuid", userInfo.getUuid());
            userObject.addProperty("type", userInfo.getType());
            jsonObject.add(username, userObject);
        }

        try {
            String jsonString = jsonObject.toString();
            Files.write(Paths.get(UUID_FILE), jsonString.getBytes());
        } catch (IOException e) {
            outputTextArea.append("Failed to save UUIDs.\n");
        }
    }

    private static UserInfo convertUsernameToUserInfo(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (response.length() > 0) {
                    // Parse the JSON response
                    JsonObject jsonObject = new JsonParser().parse(response.toString()).getAsJsonObject();
                    String uuid = jsonObject.get("id").getAsString();
                    return new UserInfo(uuid, "Unknown");
                }
            }
        } catch (IOException e) {
            outputTextArea.append("Failed to convert username to UserInfo.\n");
        }

        return null;
    }

    private static class UserInfo {
        private String uuid;
        private String type;

        public UserInfo(String uuid, String type) {
            this.uuid = uuid;
            this.type = type;
        }

        public String getUuid() {
            return uuid;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}