package com.example.weatherapp12;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class WeatherApiTest {

    private static final String API_KEY = "4e2f7ff103a422fd7a525922c12c3cdb";

    private String performGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            String errorBody = conn.getErrorStream() != null
                    ? new String(readAll(conn.getErrorStream()), StandardCharsets.UTF_8)
                    : "no body";
            conn.disconnect();
            throw new RuntimeException("HTTP " + code + ": " + errorBody);
        }

        String body = new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
        conn.disconnect();
        return body;
    }

    private byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    @Test
    public void testAllCitiesWeather() throws Exception {
        String[][] cities = {
                {"北京", "110000"}, {"上海", "310000"}, {"广州", "440100"}, {"深圳", "440300"},
                {"杭州", "330100"}, {"成都", "510100"}, {"武汉", "420100"}, {"南京", "320100"},
                {"重庆", "500000"}, {"西安", "610100"}, {"长沙", "430100"}, {"郑州", "410100"},
                {"天津", "120000"}, {"苏州", "320500"}, {"昆明", "530100"}, {"青岛", "370200"},
                {"大连", "210200"}, {"厦门", "350200"}, {"宁波", "330200"}, {"福州", "350100"},
                {"合肥", "340100"}, {"济南", "370100"}, {"沈阳", "210100"}, {"哈尔滨", "230100"},
                {"柳州", "450200"}
        };

        int passed = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        for (String[] city : cities) {
            try {
                // 1) 实况天气 (base)
                String baseJson = performGetRequest(
                        "https://restapi.amap.com/v3/weather/weatherInfo?key=" + API_KEY
                                + "&city=" + city[1] + "&extensions=base");
                JSONObject baseRoot = new JSONObject(baseJson);
                assertEquals(city[0] + " live status", "1", baseRoot.optString("status"));
                JSONArray lives = baseRoot.optJSONArray("lives");
                assertNotNull(city[0] + " lives 不应为null", lives);
                assertTrue(city[0] + " lives 不应为空", lives.length() > 0);

                JSONObject live = lives.getJSONObject(0);
                assertNotNull(city[0] + " city", live.optString("city"));
                assertNotNull(city[0] + " weather", live.optString("weather"));
                assertNotNull(city[0] + " temperature", live.optString("temperature"));
                assertNotNull(city[0] + " humidity", live.optString("humidity"));

                // 2) 预报天气 (all) — 只测部分城市减少请求
                if (city[1].equals("110000") || city[1].equals("310000") || city[1].equals("440100")
                        || city[1].equals("450200") || city[1].equals("420100")) {
                    Thread.sleep(300);
                    String allJson = performGetRequest(
                            "https://restapi.amap.com/v3/weather/weatherInfo?key=" + API_KEY
                                    + "&city=" + city[1] + "&extensions=all");
                    JSONObject allRoot = new JSONObject(allJson);
                    assertEquals(city[0] + " forecast status", "1", allRoot.optString("status"));
                    JSONArray forecasts = allRoot.optJSONArray("forecasts");
                    assertNotNull(city[0] + " forecasts", forecasts);
                    assertTrue(city[0] + " forecasts > 0", forecasts.length() > 0);
                    JSONObject fc = forecasts.getJSONObject(0);
                    JSONArray casts = fc.optJSONArray("casts");
                    assertNotNull(city[0] + " casts", casts);
                    assertTrue(city[0] + " casts >= 1", casts.length() >= 1);
                    JSONObject today = casts.getJSONObject(0);
                    assertNotNull(city[0] + " daytemp", today.optString("daytemp"));
                    System.out.println(city[0] + " 预报: " + today.optString("dayweather") + " "
                            + today.optString("daytemp") + "/" + today.optString("nighttemp") + "°C");
                }

                System.out.println("✓ " + city[0] + " (" + city[1] + ") 实况: "
                        + live.optString("weather") + " " + live.optString("temperature") + "°C");
                passed++;
                Thread.sleep(200);
            } catch (Exception e) {
                failed++;
                errors.append("\n").append(city[0]).append(" (").append(city[1]).append("): ").append(e.getMessage());
            }
        }

        String summary = "\n=== 测试结果 ==="
                + "\n总城市: " + cities.length
                + "\n通过: " + passed
                + "\n失败: " + failed
                + (failed > 0 ? "\n错误详情:" + errors : "");
        System.out.println(summary);

        assertTrue("有 " + failed + " 个城市测试失败:" + errors, failed == 0);
    }
}
