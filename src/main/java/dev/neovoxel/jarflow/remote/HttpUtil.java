package dev.neovoxel.jarflow.remote;

import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil {
    public static String get(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if (responseCode == 200) {
                java.util.Scanner scanner = new java.util.Scanner(con.getInputStream());
                return scanner.useDelimiter("\\Z").next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
