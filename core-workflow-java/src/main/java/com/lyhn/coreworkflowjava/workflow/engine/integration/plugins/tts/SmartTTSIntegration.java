package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lyhn.coreworkflowjava.workflow.engine.context.EngineContextHolder;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.util.S3ClientUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
// 将文本转化为高质量的音频文件
public class SmartTTSIntegration implements TtsIntegration {
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Value("${spark.app-id}")
    private String appId;

    @Value("${spark.api-key}")
    private String apiKey;

    @Value("${spark.api-secret}")
    private String apiSecret;

    @Value("${spark.tts-url}")
    private String ttsUrl;

    @Value("${spark.source:spark}")
    private String source;

    @Resource
    private S3ClientUtil s3ClientUtil;

    @Override
    public String source() {
        return source;
    }

    @Override
    public Map<String, Object> call(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Node node = nodeState.node();
        log.info("Executing Smart TTS node: {}", node.getId());

        // Extract parameters
        // 解析输入参数 (text, vcn, speed)
        String text = getString(inputs, "text");
        String vcn = getString(inputs, "vcn");
        Integer speed = getInteger(inputs, "speed", 50);

        // Validate required parameters
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text is required");
        }

        if (vcn == null || vcn.isEmpty()) {
            throw new IllegalArgumentException("Voice character (vcn) is required");
        }

        // Perform Smart TTS synthesis
        // 调用核心合成方法（支持智能分块+并发处理）
        byte[] audioData;
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        if (textBytes.length > 500) {
            log.info("Long text detected ({} bytes), using smart chunking + concurrent processing", textBytes.length);
            audioData = performChunkedTTSSynthesis(text, vcn, speed, appId, apiKey, apiSecret);
        } else {
            audioData = performSmartTTSSynthesis(text, vcn, speed, appId, apiKey, apiSecret);
        }

        // Upload audio file to Minio and get URL
        // 上传音频到S3
        String objectKey = "audio/" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "/" + UUID.randomUUID() + ".mp3";
        String audioUrl = s3ClientUtil.uploadObject(objectKey, "audio/mpeg", audioData);

        // Create result
        // 构建并返回标准格式的结果
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("data", Map.of("voice_url", audioUrl));
        outputs.put("code", 0);
        outputs.put("message", "Success");
        outputs.put("sid", EngineContextHolder.get().getSid());

        log.info("Smart TTS node completed: {}", node.getId());
        return outputs;
    }

    /**
     * Performs Smart TTS synthesis using WebSocket connection
     *
     * @param text      Text to synthesize
     * @param vcn       Voice character name
     * @param speed     Speech speed (1-100)
     * @param appId     Application ID
     * @param apiKey    API Key
     * @param apiSecret API Secret
     * @return Audio data as byte array
     * @throws Exception if synthesis fails
     */
    // 边合成边返回音频数据
    // 选择了WebSocket
    private byte[] performSmartTTSSynthesis(String text, String vcn, Integer speed, String appId, String apiKey, String apiSecret) throws Exception {
        log.info("Performing Smart TTS synthesis for text: {} with voice: {}, speed: {}",
                text.substring(0, Math.min(text.length(), 50)) + (text.length() > 50 ? "..." : ""),
                vcn, speed);

        // Generate authenticated WebSocket URL
        // 1. 生成鉴权 URL
        String authUrl = getAuthUrl(ttsUrl, apiKey, apiSecret);
        String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");

        // Create WebSocket listener to handle TTS response
        // 2. 创建一个 Future 来异步接收结果
        CompletableFuture<byte[]> resultFuture = new CompletableFuture<>();
        // 3. 创建 WebSocket 监听器
        TTSWebSocketListener listener = new TTSWebSocketListener(resultFuture, text, vcn, speed, appId);

        // Establish WebSocket connection
        // 4. 建立 WebSocket 连接
        Request request = new Request.Builder().url(wsUrl).build();
        // listener 在后台不断拼音频，拼完了再 complete 通知主线程收工
        WebSocket webSocket = httpClient.newWebSocket(request, listener);

        // Wait for result or timeout
        // 5. 阻塞等待结果，设置超时
        try {
            return resultFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            webSocket.close(1000, "Timeout or error");
            throw new RuntimeException("TTS synthesis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Performs chunked TTS synthesis using SmartTextChunker + ConcurrentTtsProcessor
     */
    private byte[] performChunkedTTSSynthesis(String text, String vcn, Integer speed,
                                               String appId, String apiKey, String apiSecret) throws Exception {
        SmartTextChunker chunker = new SmartTextChunker();
        ConcurrentTtsProcessor processor = new ConcurrentTtsProcessor();

        List<SmartTextChunker.TextChunk> chunks = chunker.chunk(text);
        log.info("Text split into {} chunks by SmartTextChunker", chunks.size());

        ConcurrentTtsProcessor.TtsSynthesisFunction synthesisFunction =
                (chunkText, v) -> performSmartTTSSynthesis(chunkText, v, speed, appId, apiKey, apiSecret);

        List<byte[]> audioChunks = processor.processChunks(chunks, synthesisFunction, vcn);

        byte[] merged = WavAudioMerger.mergeMp3Chunks(audioChunks);
        log.info("Chunked TTS completed: {} chunks merged into {} bytes", audioChunks.size(), merged.length);
        return merged;
    }

    /**
     * Generates authenticated URL for WebSocket connection
     */
    private String getAuthUrl(String requestUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(requestUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());

        String signatureOrigin = "host: " + url.getHost() + "\n" +
                "date: " + date + "\n" +
                "GET " + url.getPath() + " HTTP/1.1";

        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);
        byte[] signData = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signData);

        String authorizationOrigin = "api_key=\"" + apiKey + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"" + signature + "\"";
        String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        return requestUrl + "?authorization=" + URLEncoder.encode(authorization, StandardCharsets.UTF_8.name()) +
                "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8.name()) +
                "&host=" + URLEncoder.encode(url.getHost(), StandardCharsets.UTF_8.name());
    }

    /**
     * WebSocket listener for handling TTS service responses
     */
    private static class TTSWebSocketListener extends WebSocketListener {
        private final CompletableFuture<byte[]> resultFuture;
        private final String text;
        private final String vcn;
        private final Integer speed;
        private final String appId;
        private final ByteArrayOutputStream audioStream = new ByteArrayOutputStream();

        public TTSWebSocketListener(CompletableFuture<byte[]> resultFuture, String text, String vcn, Integer speed, String appId) {
            this.resultFuture = resultFuture;
            this.text = text;
            this.vcn = vcn;
            this.speed = speed;
            this.appId = appId;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            // Send TTS request when connection is established
            // 连接一建立，立刻发送合成请求
            JSONObject requestJson = buildTTSRequest();
            webSocket.send(requestJson.toString());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            // 收到服务返回的消息
            try {
                JSONObject responseJson = JSON.parseObject(text);
                if (log.isDebugEnabled()) {
                    log.debug("TTS service response: {}", responseJson);
                }

                int code = responseJson.getJSONObject("header").getIntValue("code");

                if (code != 0) {
                    String message = responseJson.getJSONObject("header").getString("message");
                    resultFuture.completeExceptionally(new RuntimeException("TTS service error: " + code + " - " + message));
                    webSocket.close(1000, "Error");
                    return;
                }

                if (responseJson.containsKey("payload")) {
                    JSONObject payload = responseJson.getJSONObject("payload");
                    if (payload.containsKey("audio")) {
                        // 从消息中提取音频数据 (base64编码)
                        String audioBase64 = payload.getJSONObject("audio").getString("audio");
                        byte[] audioData = Base64.getDecoder().decode(audioBase64);
                        audioStream.write(audioData);// 将音频片段写入流中
                    }

                    int status = payload.getJSONObject("audio").getIntValue("status");
                    // 检查合成是否结束
                    if (status == 2) {
                        // Synthesis completed
                        resultFuture.complete(audioStream.toByteArray());
                        webSocket.close(1000, "Completed");
                    }
                }
            } catch (Exception e) {
                resultFuture.completeExceptionally(e);
                webSocket.close(1000, "Error");
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            resultFuture.completeExceptionally(t);
        }


        public String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

        // 构建一个符合讯飞规范的 JSON 请求，并发送给服务器，告诉它我要合成什么文本、用什么音色等
        /**
         * Builds the TTS request JSON
         */
        private JSONObject buildTTSRequest() {
            JSONObject request = new JSONObject();

            // Header
            JSONObject header = new JSONObject();
            header.put("app_id", appId);
            header.put("status", 2); // 3 means end of input
            request.put("header", header);

            // Parameter
            JSONObject parameter = new JSONObject();
            JSONObject tts = new JSONObject();
            tts.put("vcn", vcn);
            tts.put("speed", speed);
            tts.put("volume", 50);
            tts.put("pitch", 50);
            tts.put("bgs", 0);
            tts.put("rhy", 0);
            tts.put("reg", 0);
            tts.put("rdn", 0);

            JSONObject audio = new JSONObject();
            audio.put("encoding", "lame");
            audio.put("sample_rate", 24000);
            audio.put("channels", 1);
            audio.put("bit_depth", 16);
            audio.put("frame_size", 0);
            tts.put("audio", audio);
            parameter.put("tts", tts);
            request.put("parameter", parameter);

            // Payload
            JSONObject payload = new JSONObject();
            JSONObject textPayload = new JSONObject();
            textPayload.put("encoding", "utf8");
            textPayload.put("compress", "raw");
            textPayload.put("format", "plain");
            textPayload.put("status", 2); // 3 means end of input
            textPayload.put("seq", 0);
            textPayload.put("text", Base64.getEncoder().encodeToString(this.text.getBytes(StandardCharsets.UTF_8)));
            payload.put("text", textPayload);
            request.put("payload", payload);

            return request;
        }
    }
    public String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    // Simple ByteArrayOutputStream implementation for audio data
    private static class ByteArrayOutputStream {
        // 初始容量1024字节
        private byte[] buf = new byte[1024];
        // 当前已写入字节数
        private int count = 0;

        // 将字节数组写入缓冲区
        public synchronized void write(byte[] b) {
            ensureCapacity(count + b.length);
            System.arraycopy(b, 0, buf, count, b.length);
            count += b.length;
        }

        public synchronized byte[] toByteArray() {
            return Arrays.copyOf(buf, count);
        }

        // 确保内部缓冲区有足够的容量来存储指定数量的字节
        private void ensureCapacity(int minCapacity) {
            if (minCapacity - buf.length > 0) {
                grow(minCapacity);
            }
        }

        private void grow(int minCapacity) {
            int oldCapacity = buf.length;
            int newCapacity = oldCapacity << 1;
            if (newCapacity - minCapacity < 0) {
                newCapacity = minCapacity;
            }
            buf = Arrays.copyOf(buf, newCapacity);
        }
    }

    // Simple URL class for parsing URL components
    private static class URL {
        private final String url;
        private final String host;
        private final String path;

        public URL(String url) throws Exception {
            this.url = url;
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                throw new IllegalArgumentException("Invalid URL: " + url);
            }
            int hostStart = protocolEnd + 3;
            int hostEnd = url.indexOf("/", hostStart);
            if (hostEnd == -1) {
                this.host = url.substring(hostStart);
                this.path = "/";
            } else {
                this.host = url.substring(hostStart, hostEnd);
                this.path = url.substring(hostEnd);
            }
        }

        public String getHost() {
            return host;
        }

        public String getPath() {
            return path;
        }
    }

}