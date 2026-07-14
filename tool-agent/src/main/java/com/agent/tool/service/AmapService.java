package com.agent.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmapService {

    @Value("${amap.key}")
    private String amapKey;

    private final ObjectMapper objectMapper;
    private final DeepSeekService deepSeekService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> planDrivingRoute(String from, String to, int strategy) {
        log.info("规划路线: {} -> {}", from, to);

        String origin = geocodeWithFallback(from);
        String destination = geocodeWithFallback(to);

        if (origin == null || origin.isEmpty()) {
            throw new RuntimeException("无法识别出发地: " + from);
        }
        if (destination == null || destination.isEmpty()) {
            throw new RuntimeException("无法识别目的地: " + to);
        }

        log.info("坐标解析成功: origin={}, destination={}", origin, destination);

        String url = "https://restapi.amap.com/v3/direction/driving"
                + "?key=" + amapKey
                + "&origin=" + origin
                + "&destination=" + destination
                + "&strategy=" + strategy
                + "&extensions=all";

        try {
            String response = restTemplate.getForObject(URI.create(url), String.class);
            log.info("高德路径规划响应: {}", response);
            JsonNode root = objectMapper.readTree(response);

            if (!"1".equals(root.path("status").asText())) {
                throw new RuntimeException("高德路径规划失败: " + root.path("info").asText());
            }

            JsonNode pathNode = root.path("route").path("paths").get(0);
            if (pathNode == null) {
                throw new RuntimeException("高德未返回路径数据");
            }

            // 解析 polyline：优先取 path 级别的总 polyline，否则从 steps 拼接
            List<List<Double>> pathCoords = parsePolylineFromPath(pathNode);

            Map<String, Object> result = new HashMap<>();
            result.put("distance", formatDistance(pathNode.path("distance").asText("0")));
            result.put("duration", formatDuration(pathNode.path("duration").asText("0")));
            result.put("trafficStatus", parseTrafficStatus(pathNode));
            result.put("toll", pathNode.path("tolls").asText("0") + "元");
            result.put("steps", parseSteps(pathNode.path("steps")));

            String[] originParts = origin.split(",");
            String[] destParts = destination.split(",");
            result.put("startPoint", List.of(Double.parseDouble(originParts[0]), Double.parseDouble(originParts[1])));
            result.put("endPoint", List.of(Double.parseDouble(destParts[0]), Double.parseDouble(destParts[1])));

            // 关键修复：只有解析出真实坐标才用，否则抛异常让上层走 Mock
            if (pathCoords.isEmpty()) {
                log.warn("高德返回了路径，但 polyline 解析为空，降级到 Mock");
                throw new RuntimeException("polyline 解析为空");
            }
            result.put("path", pathCoords);

            result.put("from", from);
            result.put("to", to);
            result.put("source", "amap");

            try {
                translateRouteResultToEnglish(result);
            } catch (Exception e) {
                log.warn("Failed to translate route instructions using AI", e);
            }

            return result;

        } catch (Exception e) {
            log.error("高德路径规划异常", e);
            throw new RuntimeException("路径规划失败: " + e.getMessage());
        }
    }

    public Map<String, Object> planWalkingRoute(String from, String to) {
        log.info("步行路线规划: {} -> {}", from, to);

        String origin = geocodeWithFallback(from);
        String destination = geocodeWithFallback(to);

        if (origin == null || origin.isEmpty()) {
            throw new RuntimeException("无法识别出发地: " + from);
        }
        if (destination == null || destination.isEmpty()) {
            throw new RuntimeException("无法识别目的地: " + to);
        }

        String url = "https://restapi.amap.com/v3/direction/walking"
                + "?key=" + amapKey
                + "&origin=" + origin
                + "&destination=" + destination;

        try {
            String response = restTemplate.getForObject(URI.create(url), String.class);
            log.info("高德步行规划响应: {}", response);
            JsonNode root = objectMapper.readTree(response);

            if (!"1".equals(root.path("status").asText())) {
                throw new RuntimeException("高德步行规划失败: " + root.path("info").asText());
            }

            JsonNode pathNode = root.path("route").path("paths").get(0);
            if (pathNode == null) {
                throw new RuntimeException("高德未返回步行路径数据");
            }

            List<List<Double>> pathCoords = parsePolylineFromPath(pathNode);
            if (pathCoords.isEmpty()) {
                throw new RuntimeException("步行 polyline 解析为空");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("distance", formatDistance(pathNode.path("distance").asText("0")));
            result.put("duration", formatDuration(pathNode.path("duration").asText("0")));
            result.put("trafficStatus", "步行");
            result.put("steps", parseSteps(pathNode.path("steps")));

            String[] originParts = origin.split(",");
            String[] destParts = destination.split(",");
            result.put("startPoint", List.of(Double.parseDouble(originParts[0]), Double.parseDouble(originParts[1])));
            result.put("endPoint", List.of(Double.parseDouble(destParts[0]), Double.parseDouble(destParts[1])));
            result.put("path", pathCoords);
            result.put("from", from);
            result.put("to", to);
            result.put("source", "amap");

            try {
                translateRouteResultToEnglish(result);
            } catch (Exception e) {
                log.warn("Failed to translate walking route instructions using AI", e);
            }

            return result;

        } catch (Exception e) {
            log.error("高德步行规划异常", e);
            throw new RuntimeException("步行路径规划失败: " + e.getMessage());
        }
    }

    public Map<String, Object> planTransitRoute(String from, String to) {
        log.info("公交路线规划: {} -> {}", from, to);

        String origin = geocodeWithFallback(from);
        String destination = geocodeWithFallback(to);

        if (origin == null || origin.isEmpty()) {
            throw new RuntimeException("无法识别出发地: " + from);
        }
        if (destination == null || destination.isEmpty()) {
            throw new RuntimeException("无法识别目的地: " + to);
        }

        String city = "北京";
        String cityd = "北京";

        String url = "https://restapi.amap.com/v3/direction/transit/integrated"
                + "?key=" + amapKey
                + "&origin=" + origin
                + "&destination=" + destination
                + "&city=" + city
                + "&cityd=" + cityd;

        try {
            String response = restTemplate.getForObject(URI.create(url), String.class);
            log.info("高德公交规划响应: {}", response);
            JsonNode root = objectMapper.readTree(response);

            if (!"1".equals(root.path("status").asText())) {
                throw new RuntimeException("高德公交规划失败: " + root.path("info").asText());
            }

            JsonNode routeRoot = root.path("route");

            // Transit uses "transits" instead of "paths"
            JsonNode transitNode = routeRoot.path("transits").get(0);
            if (transitNode == null) {
                throw new RuntimeException("高德未返回公交路径数据");
            }

            // Build flat steps from transit segments
            List<Map<String, String>> steps = new ArrayList<>();
            List<List<Double>> allCoords = new ArrayList<>();

            JsonNode segments = transitNode.path("segments");
            if (segments.isArray()) {
                for (JsonNode seg : segments) {
                    if (seg.has("walking")) {
                        JsonNode walkingSteps = seg.path("walking").path("steps");
                        if (walkingSteps.isArray()) {
                            for (JsonNode ws : walkingSteps) {
                                Map<String, String> step = new HashMap<>();
                                step.put("instruction", ws.path("instruction").asText(""));
                                step.put("distance", formatDistance(ws.path("distance").asText("0")));
                                step.put("duration", formatDuration(ws.path("duration").asText("0")));
                                step.put("polyline", ws.path("polyline").asText(""));
                                steps.add(step);
                                allCoords.addAll(parsePolylineString(ws.path("polyline").asText("")));
                            }
                        }
                    }
                    if (seg.has("bus")) {
                        JsonNode buslines = seg.path("bus").path("buslines");
                        if (buslines.isArray()) {
                            for (JsonNode bl : buslines) {
                                Map<String, String> step = new HashMap<>();
                                String busName = bl.path("name").asText("");
                                String dep = bl.path("departure_stop").path("name").asText("");
                                String arr = bl.path("arrival_stop").path("name").asText("");
                                String viaCount = String.valueOf(bl.path("via_stops").size());
                                step.put("instruction", "乘" + busName + "（" + dep + " → " + arr + "，经" + viaCount + "站）");
                                step.put("distance", formatDistance(bl.path("distance").asText("0")));
                                step.put("duration", formatDuration(bl.path("duration").asText("0")));
                                step.put("polyline", bl.path("polyline").asText(""));
                                steps.add(step);
                                allCoords.addAll(parsePolylineString(bl.path("polyline").asText("")));
                            }
                        }
                    }
                }
            }

            if (allCoords.isEmpty()) {
                throw new RuntimeException("公交 polyline 解析为空");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("distance", formatDistance(transitNode.path("distance").asText("0")));
            result.put("duration", formatDuration(transitNode.path("duration").asText("0")));
            result.put("trafficStatus", "公交/地铁");
            result.put("cost", transitNode.path("cost").asText("0") + "元");
            result.put("steps", steps);

            String[] originParts = origin.split(",");
            String[] destParts = destination.split(",");
            result.put("startPoint", List.of(Double.parseDouble(originParts[0]), Double.parseDouble(originParts[1])));
            result.put("endPoint", List.of(Double.parseDouble(destParts[0]), Double.parseDouble(destParts[1])));
            result.put("path", allCoords);
            result.put("from", from);
            result.put("to", to);
            result.put("source", "amap");

            try {
                translateRouteResultToEnglish(result);
            } catch (Exception e) {
                log.warn("Failed to translate transit route instructions using AI", e);
            }

            return result;

        } catch (Exception e) {
            log.error("高德公交规划异常", e);
            throw new RuntimeException("公交路径规划失败: " + e.getMessage());
        }
    }

    // ==================== 地理编码多级降级 ====================

    private String geocodeWithFallback(String address) {
        String location = geocode(address);
        if (location != null && !location.isEmpty()) return location;

        // Try POI text search first for POIs like "HKU", "Tiananmen Square", "Hong Kong Airport"
        location = placeTextSearch(address);
        if (location != null && !location.isEmpty()) {
            log.info("地理编码通过POI搜索成功: {}", address);
            return location;
        }

        if (!address.startsWith("北京") && !address.startsWith("上海") && !address.startsWith("广州")) {
            location = geocode("北京" + address);
            if (location != null && !location.isEmpty()) {
                log.info("地理编码加前缀重试成功: {}", "北京" + address);
                return location;
            }
        }

        log.warn("地理编码全部失败: {}", address);
        return null;
    }

    private String geocode(String address) {
        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = "https://restapi.amap.com/v3/geocode/geo?key=" + amapKey + "&address=" + encoded;
            String response = restTemplate.getForObject(URI.create(url), String.class);
            log.info("高德地理编码[{}]: {}", address, response);
            JsonNode root = objectMapper.readTree(response);

            if (!"1".equals(root.path("status").asText())) return null;
            JsonNode geocodes = root.path("geocodes");
            if (geocodes.isArray() && geocodes.size() > 0) {
                return geocodes.get(0).path("location").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("地理编码异常[{}]", address, e);
            return null;
        }
    }

    private String placeTextSearch(String keywords) {
        try {
            String encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String url = "https://restapi.amap.com/v3/place/text?key=" + amapKey
                    + "&keywords=" + encoded + "&offset=1&page=1";
            String response = restTemplate.getForObject(URI.create(url), String.class);
            log.info("高德POI搜索[{}]: {}", keywords, response);
            JsonNode root = objectMapper.readTree(response);

            if (!"1".equals(root.path("status").asText())) return null;
            JsonNode pois = root.path("pois");
            if (pois.isArray() && pois.size() > 0) {
                return pois.get(0).path("location").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("POI搜索异常[{}]", keywords, e);
            return null;
        }
    }

    // ==================== Polyline 解析（核心修复） ====================

    /**
     * 优先取 path 级别的 polyline，如果为空则从 steps 各段拼接
     */
    private List<List<Double>> parsePolylineFromPath(JsonNode pathNode) {
        String polyline = pathNode.path("polyline").asText("");
        log.info("解析 path 级别 polyline，长度={}, 内容前100字={}",
                polyline.length(),
                polyline.substring(0, Math.min(100, polyline.length())));

        List<List<Double>> coords = parsePolylineString(polyline);
        if (!coords.isEmpty()) {
            log.info("path 级别 polyline 解析成功，共 {} 个点", coords.size());
            return coords;
        }

        // 降级：从 steps 里每段的 polyline 拼接
        log.info("path 级别 polyline 为空，尝试从 steps 拼接");
        JsonNode steps = pathNode.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                String stepPoly = step.path("polyline").asText("");
                coords.addAll(parsePolylineString(stepPoly));
            }
        }
        log.info("steps 拼接完成，共 {} 个点", coords.size());
        return coords;
    }

    private List<List<Double>> parsePolylineString(String polyline) {
        List<List<Double>> coords = new ArrayList<>();
        if (polyline == null || polyline.trim().isEmpty()) return coords;

        String[] points = polyline.split(";");
        for (String point : points) {
            if (point.trim().isEmpty()) continue;
            String[] parts = point.split(",");
            if (parts.length >= 2) {
                try {
                    double lon = Double.parseDouble(parts[0].trim());
                    double lat = Double.parseDouble(parts[1].trim());
                    coords.add(List.of(lon, lat));
                } catch (NumberFormatException e) {
                    log.warn("坐标解析失败: {}", point);
                }
            }
        }
        return coords;
    }

    // ==================== 数据格式化工具 ====================

    private List<Map<String, String>> parseSteps(JsonNode stepsNode) {
        List<Map<String, String>> steps = new ArrayList<>();
        if (stepsNode.isArray()) {
            for (JsonNode step : stepsNode) {
                Map<String, String> s = new HashMap<>();
                s.put("instruction", step.path("instruction").asText(""));
                s.put("distance", formatDistance(step.path("distance").asText("0")));
                s.put("duration", formatDuration(step.path("duration").asText("0")));
                s.put("polyline", step.path("polyline").asText(""));
                steps.add(s);
            }
        }
        return steps;
    }

    private String formatDistance(String meters) {
        try {
            int m = Integer.parseInt(meters);
            if (m >= 1000) return String.format("%.1fkm", m / 1000.0);
            return m + "米";
        } catch (NumberFormatException e) {
            return meters;
        }
    }

    private String formatDuration(String seconds) {
        try {
            int sec = Integer.parseInt(seconds);
            if (sec >= 3600) {
                int h = sec / 3600;
                int m = (sec % 3600) / 60;
                return h + "小时" + m + "分钟";
            }
            if (sec >= 60) return (sec / 60) + "分钟";
            return sec + "秒";
        } catch (NumberFormatException e) {
            return seconds;
        }
    }

    private String parseTrafficStatus(JsonNode path) {
        try {
            int duration = Integer.parseInt(path.path("duration").asText("0"));
            int distance = Integer.parseInt(path.path("distance").asText("1"));
            double speed = (distance / (double) duration) * 3.6;
            if (speed > 40) return "畅通";
            if (speed > 20) return "缓行";
            return "拥堵";
        } catch (Exception e) {
            return "未知";
        }
    }

    @SuppressWarnings("unchecked")
    private void translateRouteResultToEnglish(Map<String, Object> result) {
        try {
            // Extract the fields we want to translate
            Map<String, Object> translationPayload = new HashMap<>();
            translationPayload.put("trafficStatus", result.get("trafficStatus"));
            translationPayload.put("toll", result.get("toll"));
            
            List<Map<String, String>> steps = (List<Map<String, String>>) result.get("steps");
            List<String> instructions = new ArrayList<>();
            for (Map<String, String> step : steps) {
                instructions.add(step.get("instruction"));
            }
            translationPayload.put("instructions", instructions);

            String rawJson = objectMapper.writeValueAsString(translationPayload);

            String systemPrompt = """
                You are a translation assistant.
                Translate the route planning data from Chinese to English.
                Keep street and location names translated accurately to their standard English or Pinyin forms.
                Translate traffic status (畅通 -> Clear, 缓行 -> Slow, 拥堵 -> Heavy Traffic, 未知 -> Unknown).
                Translate toll description (e.g. 5元 -> 5 CNY).
                Format the response strictly as a JSON object matching this structure:
                {
                  "trafficStatus": "English traffic status",
                  "toll": "English toll description",
                  "instructions": [
                    "English instruction for step 1",
                    "English instruction for step 2",
                    ...
                  ]
                }
                Only return the JSON object, do not include any other markdown code blocks.
                """;

            String aiResponse = deepSeekService.chat(systemPrompt, rawJson);
            JsonNode root = objectMapper.readTree(aiResponse);

            if (root.has("trafficStatus")) {
                result.put("trafficStatus", root.path("trafficStatus").asText());
            }
            if (root.has("toll")) {
                result.put("toll", root.path("toll").asText());
            }
            if (root.has("instructions")) {
                JsonNode insts = root.path("instructions");
                for (int i = 0; i < steps.size() && i < insts.size(); i++) {
                    steps.get(i).put("instruction", insts.get(i).asText());
                }
            }

            // Translate duration units and distance units to general English
            String duration = (String) result.get("duration");
            if (duration != null) {
                result.put("duration", duration.replace("小时", "h ").replace("分钟", "m ").replace("秒", "s"));
            }
            String distance = (String) result.get("distance");
            if (distance != null) {
                result.put("distance", distance.replace("米", "m").replace("公里", "km"));
            }
            for (Map<String, String> step : steps) {
                String sd = step.get("distance");
                if (sd != null) step.put("distance", sd.replace("米", "m").replace("公里", "km"));
                String st = step.get("duration");
                if (st != null) step.put("duration", st.replace("小时", "h ").replace("分钟", "m ").replace("秒", "s"));
            }
        } catch (Exception e) {
            log.error("AI translation of route results failed", e);
        }
    }
}