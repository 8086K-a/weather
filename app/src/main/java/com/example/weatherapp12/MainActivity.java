package com.example.weatherapp12;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主活动类
 * 负责展示天气界面，并通过高德地图API获取并解析柳州市的实时天气和预报数据
 */

public class MainActivity extends AppCompatActivity {

    // 高德地图 Web服务 API Key
    private static final String API_KEY = "4e2f7ff103a422fd7a525922c12c3cdb";
    
    private static final String[][] CITIES = {
        {"北京市", "110000"},
        {"上海市", "310000"},
        {"广州市", "440100"},
        {"深圳市", "440300"},
        {"杭州市", "330100"},
        {"成都市", "510100"},
        {"武汉市", "420100"},
        {"南京市", "320100"},
        {"重庆市", "500000"},
        {"西安市", "610100"},
        {"长沙市", "430100"},
        {"郑州市", "410100"},
        {"天津市", "120000"},
        {"苏州市", "320500"},
        {"昆明市", "530100"},
        {"青岛市", "370200"},
        {"大连市", "210200"},
        {"厦门市", "350200"},
        {"宁波市", "330200"},
        {"福州市", "350100"},
        {"合肥市", "340100"},
        {"济南市", "370100"},
        {"沈阳市", "210100"},
        {"哈尔滨市", "230100"},
        {"柳州市", "450200"}
    };

    private String currentCityName = "柳州市";
    private String currentCityAdcode = "450200";

    // DeepSeek OpenAI 兼容接口配置
    private static final String AI_BASE_URL = "https://api.deepseek.com";
    private static final String AI_MODEL = "deepseek-v4-flash";
    private static final String AI_API_KEY = "sk-9fef43d9630747a7bbe828b976ffeb7b";
    private static final String CACHE_PREFS = "weather_cache";

    // UI 控件声明
    private TextView tvLocation, tvDate, tvTemp, tvConditionSummary, tvIcon, tvFeelsLike;
    private TextView tvHumidity, tvWind;
    private TextView tvHourSlotNow, tvHourSlot2, tvHourSlot3, tvHourSlot4, tvHourSlot5, tvHourSlot6;
    private TextView tvFutureDay1, tvFutureWeather1, tvFutureIcon1, tvFutureLow1, tvFutureHigh1;
    private TextView tvFutureDay2, tvFutureWeather2, tvFutureIcon2, tvFutureLow2, tvFutureHigh2;
    private TextView tvFutureDay3, tvFutureWeather3, tvFutureIcon3, tvFutureLow3, tvFutureHigh3;
    private TextView tvFutureDay4, tvFutureWeather4, tvFutureIcon4, tvFutureLow4, tvFutureHigh4;
    private FloatingActionButton btnAiFloat;
    private LinearLayout aiPanel;
    private ImageView btnCloseAi;
    private ScrollView chatScrollView;
    private TextView tvAiChat;
    private EditText etAiInput;
    private Button btnSendAi, btnStopAi;

    private final StringBuilder chatHistory = new StringBuilder();
    private boolean isAiResponding = false;
    private boolean hasAutoAdviceRequested = false;
    private boolean hasShownAnyAiReply = false;
    private volatile boolean shouldStopAiResponse = false;
    private volatile HttpURLConnection currentAiConnection;

    private volatile String weatherFetchAdcode = "";

    // 给 AI 的天气上下文字段
    private String weatherCity = "柳州市";
    private String weatherDesc = "未知";
    private String weatherTemp = "-";
    private String weatherHumidity = "-";
    private String weatherWind = "-";
    private String weatherHigh = "-";
    private String weatherLow = "-";
    private String weatherTime = "-";

    // 单线程线程池，用于执行网络请求，避免阻塞主线程（UI线程）
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private SharedPreferences cachePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 启用沉浸式边缘到边缘显示
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // 处理系统窗口插入（如状态栏和导航栏），避免内容被遮挡
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化视图
        initViews();
        cachePrefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE);
        currentCityAdcode = cachePrefs.getString("adcode", "450200");
        currentCityName = cachePrefs.getString("city", "柳州市");
        loadCachedWeather();
        initAiPanel();
        // 获取天气数据
        fetchWeatherData();
    }

    /**
     * 绑定布局文件中的各个 TextView 控件
     */
    private void initViews() {
        tvLocation = findViewById(R.id.tvLocation);
        tvDate = findViewById(R.id.tvDate);
        tvTemp = findViewById(R.id.tvTemp);
        tvConditionSummary = findViewById(R.id.tvConditionSummary);
        tvIcon = findViewById(R.id.tvIcon);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvHourSlotNow = findViewById(R.id.tvHourSlotNow);
        tvHourSlot2 = findViewById(R.id.tvHourSlot2);
        tvHourSlot3 = findViewById(R.id.tvHourSlot3);
        tvHourSlot4 = findViewById(R.id.tvHourSlot4);
        tvHourSlot5 = findViewById(R.id.tvHourSlot5);
        tvHourSlot6 = findViewById(R.id.tvHourSlot6);

        tvFutureDay1 = findViewById(R.id.tvFutureDay1);
        tvFutureWeather1 = findViewById(R.id.tvFutureWeather1);
        tvFutureIcon1 = findViewById(R.id.tvFutureIcon1);
        tvFutureLow1 = findViewById(R.id.tvFutureLow1);
        tvFutureHigh1 = findViewById(R.id.tvFutureHigh1);

        tvFutureDay2 = findViewById(R.id.tvFutureDay2);
        tvFutureWeather2 = findViewById(R.id.tvFutureWeather2);
        tvFutureIcon2 = findViewById(R.id.tvFutureIcon2);
        tvFutureLow2 = findViewById(R.id.tvFutureLow2);
        tvFutureHigh2 = findViewById(R.id.tvFutureHigh2);

        tvFutureDay3 = findViewById(R.id.tvFutureDay3);
        tvFutureWeather3 = findViewById(R.id.tvFutureWeather3);
        tvFutureIcon3 = findViewById(R.id.tvFutureIcon3);
        tvFutureLow3 = findViewById(R.id.tvFutureLow3);
        tvFutureHigh3 = findViewById(R.id.tvFutureHigh3);

        tvFutureDay4 = findViewById(R.id.tvFutureDay4);
        tvFutureWeather4 = findViewById(R.id.tvFutureWeather4);
        tvFutureIcon4 = findViewById(R.id.tvFutureIcon4);
        tvFutureLow4 = findViewById(R.id.tvFutureLow4);
        tvFutureHigh4 = findViewById(R.id.tvFutureHigh4);

        btnAiFloat = findViewById(R.id.btnAiFloat);
        aiPanel = findViewById(R.id.aiPanel);
        btnCloseAi = findViewById(R.id.btnCloseAi);
        chatScrollView = findViewById(R.id.chatScrollView);
        tvAiChat = findViewById(R.id.tvAiChat);
        etAiInput = findViewById(R.id.etAiInput);
        btnSendAi = findViewById(R.id.btnSendAi);
        btnStopAi = findViewById(R.id.btnStopAi);

        findViewById(R.id.layoutCitySelector).setOnClickListener(v -> showCityPicker());
    }

    private void initAiPanel() {
        if (tvAiChat != null) {
            chatHistory.setLength(0);
            chatHistory.append("AI：你好，我可以根据今天的天气给你出行和穿衣建议。\n");
            tvAiChat.setText(chatHistory.toString());
        }

        if (btnAiFloat != null) {
            btnAiFloat.setOnClickListener(v -> {
                if (aiPanel != null) {
                    aiPanel.setVisibility(View.VISIBLE);
                }
                if (!isAiResponding && !hasShownAnyAiReply && !hasAutoAdviceRequested) {
                    requestDefaultAdviceIfNeeded();
                }
            });
        }

        if (btnCloseAi != null) {
            btnCloseAi.setOnClickListener(v -> {
                if (aiPanel != null) {
                    aiPanel.setVisibility(View.GONE);
                }
            });
        }

        if (btnSendAi != null) {
            btnSendAi.setOnClickListener(v -> {
                if (isAiResponding) {
                    Toast.makeText(MainActivity.this, "AI 正在回复，请稍候", Toast.LENGTH_SHORT).show();
                    return;
                }
                String userInput = etAiInput != null ? etAiInput.getText().toString().trim() : "";
                if (TextUtils.isEmpty(userInput)) {
                    Toast.makeText(MainActivity.this, "请输入问题", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (etAiInput != null) {
                    etAiInput.setText("");
                }
                appendChatLine("我", userInput, true);
                requestAiAdviceStream(userInput);
            });
        }

        if (btnStopAi != null) {
            btnStopAi.setOnClickListener(v -> stopCurrentAiResponse());
        }
    }

    /**
     * 在后台线程中发起网络请求获取天气数据
     */
    private void fetchWeatherData() {
        String adcode = currentCityAdcode;
        weatherFetchAdcode = adcode;
        executorService.execute(() -> {
            for (int retry = 0; retry < 3; retry++) {
                if (retry > 0) {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                }
                try {
                    String baseUrl = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + API_KEY + "&city=" + adcode + "&extensions=base";
                    String baseJson = performGetRequest(baseUrl);

                    String allUrl = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + API_KEY + "&city=" + adcode + "&extensions=all";
                    String allJson = performGetRequest(allUrl);

                    if (adcode.equals(weatherFetchAdcode)) {
                        parseAndUpdateUI(baseJson, allJson);
                    }
                    return;
                } catch (Exception e) {
                    boolean isDns = e instanceof UnknownHostException;
                    Log.e("WeatherApp", "获取天气数据出错 " + adcode + " (重试" + (retry + 1) + "/3)"
                            + " | " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                    if (retry == 2 || !isDns) {
                        if (adcode.equals(weatherFetchAdcode)) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "获取天气失败，请检查网络", Toast.LENGTH_SHORT).show());
                        }
                        break;
                    }
                }
            }
        });
    }

    /**
     * 封装的标准 HTTP GET 请求方法
     * 
     * @param urlStr 请求的 URL 字符串
     * @return 服务器响应的字符串内容
     * @throws Exception 网络连接或读取异常
     */
    private String performGetRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            String err = "";
            try {
                if (conn.getErrorStream() != null) {
                    err = " body:" + readStream(conn.getErrorStream());
                }
            } catch (Exception ignored) {}
            conn.disconnect();
            throw new IOException("HTTP " + code + err);
        }

        String body = readStream(conn.getInputStream());
        conn.disconnect();
        return body;
    }

    /**
     * 解析高德地图 API 返回的 JSON 字符串，并在主线程中更新 UI 控件
     * 
     * @param baseJsonStr 实况天气的 JSON 字符串
     * @param allJsonStr  预报天气的 JSON 字符串
     */
    private void parseAndUpdateUI(String baseJsonStr, String allJsonStr) {
        try {
            // 解析实况天气 JSON
            JSONObject baseJson = new JSONObject(baseJsonStr);
            JSONArray lives = baseJson.getJSONArray("lives");
            JSONObject live = lives.getJSONObject(0);

            // 提取关键字段
            String province = live.getString("province");
            String city = live.getString("city");
            String weather = live.getString("weather");         // 天气现象（如：多云）
            String temp = live.getString("temperature");        // 当前温度
            String windDir = live.getString("winddirection");   // 风向
            String windPower = live.getString("windpower");     // 风力级别
            String humidity = live.getString("humidity");       // 湿度比例
            String reportTime = live.getString("reporttime");   // 数据发布时间 e.g. "2024-05-20 15:00:00"

            // 解析预报天气 JSON，以获取今天的最高和最低气温
            JSONObject allJson = new JSONObject(allJsonStr);
            JSONArray forecasts = allJson.getJSONArray("forecasts");
            JSONObject forecast = forecasts.getJSONObject(0);
            JSONArray casts = forecast.getJSONArray("casts");
            JSONObject todayCast = casts.getJSONObject(0);      // 数组的第0项为今天的预报
            
            String dayTemp = todayCast.getString("daytemp");    // 白天最高温度
            String nightTemp = todayCast.getString("nighttemp");// 夜间最低温度

            saveWeatherCache(city, weather, temp, humidity, windDir, windPower, dayTemp, nightTemp, reportTime, casts);

            weatherCity = city;
            weatherDesc = weather;
            weatherTemp = temp;
            weatherHumidity = humidity;
            weatherWind = windDir + "风 " + windPower + "级";
            weatherHigh = dayTemp;
            weatherLow = nightTemp;
            weatherTime = reportTime;
            
            // 根据天气情况字符串匹配对应的 Emoji 图标
            String iconEmoji = getWeatherIcon(weather);

            // UI 更新必须在主线程中进行
            runOnUiThread(() -> {
                if (tvLocation != null) tvLocation.setText(city);
                if (tvDate != null) tvDate.setText(formatReportTimeForDisplay(reportTime));
                if (tvTemp != null) tvTemp.setText(temp + "°");
                if (tvIcon != null) tvIcon.setText(iconEmoji);
                if (tvConditionSummary != null) tvConditionSummary.setText(weather + "  |  最高 " + dayTemp + "°  最低 " + nightTemp + "°");
                // API 未直接提供体感温度，这里使用当前温度作为替代展示
                if (tvFeelsLike != null) tvFeelsLike.setText("体感温度 " + temp + "°");
                if (tvHumidity != null) tvHumidity.setText(humidity + "%");
                if (tvWind != null) tvWind.setText(windDir + "风 " + windPower + "级");
                updateHourlyTimeSlots(reportTime);
                updateFutureForecast(casts);
            });
        } catch (Exception e) {
            Log.e("WeatherApp", "解析天气 JSON 数据出错", e);
        }
    }

    private void updateFutureForecast(JSONArray casts) {
        if (casts == null) {
            return;
        }
        try {
            bindFutureRow(casts, 0, tvFutureDay1, tvFutureWeather1, tvFutureIcon1, tvFutureLow1, tvFutureHigh1);
            bindFutureRow(casts, 1, tvFutureDay2, tvFutureWeather2, tvFutureIcon2, tvFutureLow2, tvFutureHigh2);
            bindFutureRow(casts, 2, tvFutureDay3, tvFutureWeather3, tvFutureIcon3, tvFutureLow3, tvFutureHigh3);
            bindFutureRow(casts, 3, tvFutureDay4, tvFutureWeather4, tvFutureIcon4, tvFutureLow4, tvFutureHigh4);
        } catch (Exception e) {
            Log.w("WeatherApp", "更新未来预报失败", e);
        }
    }

    private void bindFutureRow(JSONArray casts, int index,
                               TextView dayView,
                               TextView weatherView,
                               TextView iconView,
                               TextView lowView,
                               TextView highView) {
        if (dayView == null || weatherView == null || iconView == null || lowView == null || highView == null) {
            return;
        }
        if (index >= casts.length()) {
            return;
        }
        JSONObject cast = casts.optJSONObject(index);
        if (cast == null) {
            return;
        }

        String dateText = cast.optString("date", "");
        String dayWeather = cast.optString("dayweather", "-");
        String dayTemp = cast.optString("daytemp", "-");
        String nightTemp = cast.optString("nighttemp", "-");

        dayView.setText(getFutureDayLabel(dateText, index));
        weatherView.setText(dayWeather);
        iconView.setText(getWeatherIcon(dayWeather));
        lowView.setText(nightTemp + "°");
        highView.setText(dayTemp + "°");
    }

    private String getFutureDayLabel(String dateText, int index) {
        if (index == 0) {
            return "今天";
        }
        if (index == 1) {
            return "明天";
        }
        try {
            if (!TextUtils.isEmpty(dateText)) {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = parser.parse(dateText);
                if (date != null) {
                    SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.CHINA);
                    return weekday.format(date).replace("星期", "周");
                }
            }
        } catch (Exception ignore) {
        }
        return "第" + (index + 1) + "天";
    }

    private void updateHourlyTimeSlots(String reportTime) {
        if (TextUtils.isEmpty(reportTime)) {
            return;
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date baseTime = parser.parse(reportTime);
            if (baseTime == null) {
                return;
            }
            long baseMillis = baseTime.getTime();
            long oneHourMillis = 60L * 60L * 1000L;
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:00", Locale.getDefault());

            if (tvHourSlotNow != null) {
                tvHourSlotNow.setText("现在");
            }
            if (tvHourSlot2 != null) {
                tvHourSlot2.setText(timeFormat.format(new Date(baseMillis + oneHourMillis)));
            }
            if (tvHourSlot3 != null) {
                tvHourSlot3.setText(timeFormat.format(new Date(baseMillis + oneHourMillis * 2)));
            }
            if (tvHourSlot4 != null) {
                tvHourSlot4.setText(timeFormat.format(new Date(baseMillis + oneHourMillis * 3)));
            }
            if (tvHourSlot5 != null) {
                tvHourSlot5.setText(timeFormat.format(new Date(baseMillis + oneHourMillis * 4)));
            }
            if (tvHourSlot6 != null) {
                tvHourSlot6.setText(timeFormat.format(new Date(baseMillis + oneHourMillis * 5)));
            }
        } catch (Exception e) {
            Log.w("WeatherApp", "更新时间段失败", e);
        }
    }

    private String formatReportTimeForDisplay(String reportTime) {
        try {
            Date date;
            if (!TextUtils.isEmpty(reportTime)) {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                date = parser.parse(reportTime);
            } else {
                date = null;
            }
            if (date == null) {
                date = new Date();
            }
            SimpleDateFormat displayFormat = new SimpleDateFormat("M月d日 EEEE HH:mm", Locale.CHINA);
            return "更新时间: " + displayFormat.format(date);
        } catch (Exception e) {
            SimpleDateFormat fallback = new SimpleDateFormat("M月d日 EEEE HH:mm", Locale.CHINA);
            return "更新时间: " + fallback.format(new Date());
        }
    }

    /**
     * 辅助方法：根据中文天气描述返回对应的 Emoji 表情图标
     * 
     * @param weather 天气字符串 (例如 "晴", "多云", "小雨")
     * @return 对应的 Emoji 字符串
     */
    private String getWeatherIcon(String weather) {
        if (weather.contains("晴")) return "☀️";
        if (weather.contains("多云")) return "⛅";
        if (weather.contains("阴")) return "☁️";
        if (weather.contains("雨")) return "🌧️";
        if (weather.contains("雪")) return "❄️";
        if (weather.contains("雷")) return "⛈️";
        if (weather.contains("雾") || weather.contains("霾")) return "🌫️";
        // 默认返回多云图标
        return "⛅";
    }

    private void requestAiAdviceStream(String userInput) {
        shouldStopAiResponse = false;
        setAiRespondingState(true);

        appendAssistantPrefix();

        executorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(AI_BASE_URL + "/v1/chat/completions");
                conn = (HttpURLConnection) url.openConnection();
                currentAiConnection = conn;
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(120000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + AI_API_KEY);

                JSONObject payload = buildAiRequestBody(userInput, true);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream outputStream = conn.getOutputStream();
                outputStream.write(body);
                outputStream.flush();
                outputStream.close();

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    String errorText = readStream(conn.getErrorStream());
                    throw new RuntimeException("AI 接口错误(" + code + "): " + errorText);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                StringBuilder visibleContent = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (shouldStopAiResponse) {
                        break;
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    if (data.isEmpty()) {
                        continue;
                    }

                    JSONObject chunk = new JSONObject(data);
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        continue;
                    }

                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) {
                        continue;
                    }

                    String token = getStreamText(delta, "content");
                    if (!TextUtils.isEmpty(token)) {
                        visibleContent.append(token);
                        appendAssistantToken(token);
                    }
                }
                reader.close();

                if (!shouldStopAiResponse && visibleContent.length() == 0) {
                    String fallback = requestAiAdviceNonStream(userInput);
                    if (!TextUtils.isEmpty(fallback)) {
                        appendAssistantToken(fallback);
                    }
                }
                if (!shouldStopAiResponse) {
                    appendAssistantToken("\n");
                }
            } catch (Exception e) {
                if (!shouldStopAiResponse) {
                    Log.e("WeatherApp", "AI 流式对话失败", e);
                    appendAssistantToken("\n[提示] AI 暂时不可用，请稍后重试。\n");
                }
            } finally {
                currentAiConnection = null;
                if (conn != null) {
                    conn.disconnect();
                }
                shouldStopAiResponse = false;
                setAiRespondingState(false);
            }
        });
    }

    private void stopCurrentAiResponse() {
        if (!isAiResponding) {
            Toast.makeText(this, "当前没有进行中的回复", Toast.LENGTH_SHORT).show();
            return;
        }
        shouldStopAiResponse = true;
        HttpURLConnection conn = currentAiConnection;
        if (conn != null) {
            conn.disconnect();
        }
        appendAssistantToken("\n[提示] 已停止本次回复。\n");
        setAiRespondingState(false);
    }

    private void setAiRespondingState(boolean responding) {
        isAiResponding = responding;
        runOnUiThread(() -> {
            if (btnSendAi != null) {
                btnSendAi.setEnabled(!responding);
            }
            if (btnStopAi != null) {
                btnStopAi.setVisibility(responding ? View.VISIBLE : View.GONE);
                btnStopAi.setEnabled(responding);
            }
        });
    }

    private void requestDefaultAdviceIfNeeded() {
        if (hasAutoAdviceRequested) {
            return;
        }
        hasAutoAdviceRequested = true;
        requestAiAdviceStream(getDefaultAdvicePrompt());
    }

    private String getDefaultAdvicePrompt() {
        return "请直接给出今天的两条建议："
                + "1）出行建议；2）穿衣建议。"
                + "每条建议都要简短、可执行，必要时提醒是否带伞或防晒。";
    }

    private JSONObject buildAiRequestBody(String userInput, boolean stream) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", AI_MODEL);
        payload.put("stream", stream);
        payload.put("temperature", 0.7);

        JSONArray messages = new JSONArray();

        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", "你是贴心的天气生活助手。请基于用户当地当天天气，给出简洁实用的出行和穿衣建议，语气自然，优先给可执行建议。若天气不稳定，提醒携带雨具或防晒。回答控制在5行以内。");
        messages.put(system);

        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", "天气数据：\n" + buildWeatherContext() + "\n\n用户问题：" + userInput);
        messages.put(user);

        payload.put("messages", messages);
        return payload;
    }

    private String requestAiAdviceNonStream(String userInput) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(AI_BASE_URL + "/v1/chat/completions");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + AI_API_KEY);

            JSONObject payload = buildAiRequestBody(userInput, false);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = conn.getOutputStream();
            outputStream.write(body);
            outputStream.flush();
            outputStream.close();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return "";
            }

            String responseText = readStream(conn.getInputStream());
            if (TextUtils.isEmpty(responseText)) {
                return "";
            }

            JSONObject response = new JSONObject(responseText);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return "";
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                return "";
            }

            return message.optString("content", "");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildWeatherContext() {
        return "城市：" + weatherCity + "\n"
                + "天气：" + weatherDesc + "\n"
                + "当前温度：" + weatherTemp + "°C\n"
                + "最高/最低：" + weatherHigh + "°C/" + weatherLow + "°C\n"
                + "湿度：" + weatherHumidity + "%\n"
                + "风力：" + weatherWind + "\n"
                + "更新时间：" + weatherTime;
    }

    private void appendChatLine(String role, String text, boolean withNewLine) {
        runOnUiThread(() -> {
            chatHistory.append(role).append("：").append(text);
            if (withNewLine) {
                chatHistory.append("\n");
            }
            if (tvAiChat != null) {
                tvAiChat.setText(chatHistory.toString());
            }
            scrollChatToBottom();
        });
    }

    private void appendAssistantPrefix() {
        runOnUiThread(() -> {
            chatHistory.append("AI：");
            if (tvAiChat != null) {
                tvAiChat.setText(chatHistory.toString());
            }
            scrollChatToBottom();
        });
    }

    private void appendAssistantToken(String token) {
        runOnUiThread(() -> {
            chatHistory.append(token);
            if (!TextUtils.isEmpty(token.trim())) {
                hasShownAnyAiReply = true;
            }
            if (tvAiChat != null) {
                tvAiChat.setText(chatHistory.toString());
            }
            scrollChatToBottom();
        });
    }

    private void scrollChatToBottom() {
        if (chatScrollView != null) {
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String readStream(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getStreamText(JSONObject delta, String key) {
        Object value = delta.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (!(value instanceof String)) {
            return "";
        }
        String text = ((String) value).trim();
        if ("null".equalsIgnoreCase(text)) {
            return "";
        }
        return (String) value;
    }

    private void loadCachedWeather() {
        if (cachePrefs == null) {
            return;
        }
        String city = cachePrefs.getString("city", "");
        if (TextUtils.isEmpty(city)) {
            applyLoadingPlaceholder();
            return;
        }

        String weather = cachePrefs.getString("weather", "--");
        String temp = cachePrefs.getString("temp", "--");
        String humidity = cachePrefs.getString("humidity", "--");
        String windText = cachePrefs.getString("windText", "--");
        String dayTemp = cachePrefs.getString("dayTemp", "--");
        String nightTemp = cachePrefs.getString("nightTemp", "--");
        String reportTime = cachePrefs.getString("reportTime", "");

        weatherCity = city;
        weatherDesc = weather;
        weatherTemp = temp;
        weatherHumidity = humidity;
        weatherWind = windText;
        weatherHigh = dayTemp;
        weatherLow = nightTemp;
        weatherTime = reportTime;

        if (tvLocation != null) tvLocation.setText(city);
        if (tvDate != null) tvDate.setText(formatReportTimeForDisplay(reportTime));
        if (tvTemp != null) tvTemp.setText(temp + "°");
        if (tvIcon != null) tvIcon.setText(getWeatherIcon(weather));
        if (tvConditionSummary != null) tvConditionSummary.setText(weather + "  |  最高 " + dayTemp + "°  最低 " + nightTemp + "°");
        if (tvFeelsLike != null) tvFeelsLike.setText("体感温度 " + temp + "°");
        if (tvHumidity != null) tvHumidity.setText(humidity + "%");
        if (tvWind != null) tvWind.setText(windText);

        updateHourlyTimeSlots(reportTime);
        applyCachedFutureRow(1, tvFutureDay1, tvFutureWeather1, tvFutureIcon1, tvFutureLow1, tvFutureHigh1);
        applyCachedFutureRow(2, tvFutureDay2, tvFutureWeather2, tvFutureIcon2, tvFutureLow2, tvFutureHigh2);
        applyCachedFutureRow(3, tvFutureDay3, tvFutureWeather3, tvFutureIcon3, tvFutureLow3, tvFutureHigh3);
        applyCachedFutureRow(4, tvFutureDay4, tvFutureWeather4, tvFutureIcon4, tvFutureLow4, tvFutureHigh4);
    }

    private void applyLoadingPlaceholder() {
        if (tvLocation != null) tvLocation.setText("加载中...");
        if (tvDate != null) tvDate.setText("更新时间: --");
        if (tvTemp != null) tvTemp.setText("--°");
        if (tvConditionSummary != null) tvConditionSummary.setText("--  |  最高 --°  最低 --°");
        if (tvFeelsLike != null) tvFeelsLike.setText("体感温度 --°");
        if (tvHumidity != null) tvHumidity.setText("--%");
        if (tvWind != null) tvWind.setText("--");
    }

    private void applyCachedFutureRow(int index,
                                      TextView dayView,
                                      TextView weatherView,
                                      TextView iconView,
                                      TextView lowView,
                                      TextView highView) {
        if (cachePrefs == null || dayView == null || weatherView == null || iconView == null || lowView == null || highView == null) {
            return;
        }
        String day = cachePrefs.getString("futureDay" + index, "");
        String weather = cachePrefs.getString("futureWeather" + index, "");
        String low = cachePrefs.getString("futureLow" + index, "");
        String high = cachePrefs.getString("futureHigh" + index, "");
        if (TextUtils.isEmpty(day) || TextUtils.isEmpty(weather)) {
            return;
        }
        dayView.setText(day);
        weatherView.setText(weather);
        iconView.setText(getWeatherIcon(weather));
        lowView.setText(low + "°");
        highView.setText(high + "°");
    }

    private void saveWeatherCache(String city,
                                  String weather,
                                  String temp,
                                  String humidity,
                                  String windDir,
                                  String windPower,
                                  String dayTemp,
                                  String nightTemp,
                                  String reportTime,
                                  JSONArray casts) {
        if (cachePrefs == null) {
            return;
        }
        SharedPreferences.Editor editor = cachePrefs.edit();
        editor.putString("adcode", currentCityAdcode);
        editor.putString("city", city);
        editor.putString("weather", weather);
        editor.putString("temp", temp);
        editor.putString("humidity", humidity);
        editor.putString("windText", windDir + "风 " + windPower + "级");
        editor.putString("dayTemp", dayTemp);
        editor.putString("nightTemp", nightTemp);
        editor.putString("reportTime", reportTime);

        saveFutureRow(editor, casts, 0, 1);
        saveFutureRow(editor, casts, 1, 2);
        saveFutureRow(editor, casts, 2, 3);
        saveFutureRow(editor, casts, 3, 4);
        editor.apply();
    }

    private void saveFutureRow(SharedPreferences.Editor editor, JSONArray casts, int castIndex, int rowIndex) {
        if (editor == null || casts == null || castIndex >= casts.length()) {
            return;
        }
        JSONObject cast = casts.optJSONObject(castIndex);
        if (cast == null) {
            return;
        }
        String dateText = cast.optString("date", "");
        String dayWeather = cast.optString("dayweather", "");
        String dayTemp = cast.optString("daytemp", "");
        String nightTemp = cast.optString("nighttemp", "");

        editor.putString("futureDay" + rowIndex, getFutureDayLabel(dateText, castIndex));
        editor.putString("futureWeather" + rowIndex, dayWeather);
        editor.putString("futureLow" + rowIndex, nightTemp);
        editor.putString("futureHigh" + rowIndex, dayTemp);
    }

    private void showCityPicker() {
        String[] names = new String[CITIES.length];
        for (int i = 0; i < CITIES.length; i++) {
            names[i] = CITIES[i][0];
        }
        new AlertDialog.Builder(this)
                .setTitle("选择城市")
                .setItems(names, (dialog, which) -> {
                    String name = CITIES[which][0];
                    String adcode = CITIES[which][1];
                    if (!adcode.equals(currentCityAdcode)) {
                        currentCityName = name;
                        currentCityAdcode = adcode;
                        cachePrefs.edit().putString("adcode", adcode).putString("city", name).apply();
                        hasAutoAdviceRequested = false;
                        hasShownAnyAiReply = false;
                        fetchWeatherData();
                        Toast.makeText(MainActivity.this, "已切换到 " + name, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }
}
