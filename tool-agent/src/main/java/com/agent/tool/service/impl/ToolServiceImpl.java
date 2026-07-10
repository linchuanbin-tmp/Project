package com.agent.tool.service.impl;

import com.agent.tool.dto.ToolRequest;
import com.agent.tool.dto.ToolResponse;
import com.agent.tool.service.AmapService;
import com.agent.tool.service.DeepSeekService;
import com.agent.tool.service.MeetingRoomService;
import com.agent.tool.service.ToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ToolServiceImpl implements ToolService {

    private final ObjectMapper objectMapper;
    private final DeepSeekService deepSeekService;
    private final AmapService amapService;
    private final MeetingRoomService meetingRoomService;

    public ToolServiceImpl(ObjectMapper objectMapper, DeepSeekService deepSeekService, AmapService amapService, MeetingRoomService meetingRoomService) {
        this.objectMapper = objectMapper;
        this.deepSeekService = deepSeekService;
        this.amapService = amapService;
        this.meetingRoomService = meetingRoomService;
    }

    @Override
    public ToolResponse execute(ToolRequest request) {
        log.info("Tool执行: type={}, nl={}", request.getToolType(), request.getNaturalLanguage());

        ToolResponse response = new ToolResponse();
        response.setToolType(request.getToolType());

        try {
            String nl = request.getNaturalLanguage();
            if (nl == null || nl.isEmpty()) nl = "用户未提供描述";

            switch (request.getToolType()) {
                case "MEETING_QUERY":
                    response.setData(handleMeetingQuery(nl));
                    break;
                case "SCHEDULE_CHECK":
                    response.setData(handleScheduleCheck(nl));
                    break;
                case "ROUTE_PLAN":
                    response.setData(handleRoutePlan(nl, request.getParameters()));
                    break;
                case "AI":
                case "NLP":
                    response.setData(handleAiIntent(nl));
                    break;
                default:
                    response.setSuccess(false);
                    response.setMessage("未知工具类型: " + request.getToolType());
                    return response;
            }

            response.setSuccess(true);
            response.setMessage("执行成功");

        } catch (Exception e) {
            log.error("Tool执行失败, 降级到Mock", e);
            response.setData(fallbackMock(request.getToolType()));
            response.setSuccess(true);
            response.setMessage("AI服务异常，返回演示数据");
        }

        return response;
    }

    // ==================== AI 意图识别与自动路由 ====================
    private Map<String, Object> handleAiIntent(String naturalLanguage) {
        String systemPrompt = """
            你是智能任务路由助手。分析用户的自然语言请求，判断意图并提取参数。
            只返回JSON，不要其他文字。
            
            可能的意图类型：
            - "ROUTE_PLAN": 路线规划，需要提取 from(出发地), to(目的地), mode(出行方式，默认driving)
            - "MEETING_QUERY": 会议室查询，需要提取 date(日期), capacity(人数), equipment(设备数组，如["投影仪","白板"])
            - "SCHEDULE_CHECK": 日程冲突检测，需要提取 timeRange(时间范围描述), attendees(参会人员数组)
            
            返回格式：
            {
                "intent": "ROUTE_PLAN|MEETING_QUERY|SCHEDULE_CHECK",
                "parameters": {
                    "from": "...",
                    "to": "...",
                    "mode": "driving"
                },
                "reasoning": "分析过程"
            }
            如果信息缺失，给出合理默认值。date如果用户说今天，就返回"today"。
            """;

        String aiJson = deepSeekService.chat(systemPrompt, naturalLanguage);
        JsonNode aiResult = parseJson(aiJson);

        String intent = aiResult.path("intent").asText("MEETING_QUERY").toUpperCase();
        JsonNode paramsNode = aiResult.path("parameters");
        Map<String, Object> params = new HashMap<>();

        if (paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    params.put(entry.getKey(), value.asText());
                } else if (value.isInt()) {
                    params.put(entry.getKey(), value.asInt());
                } else if (value.isArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonNode n : value) list.add(n.asText());
                    params.put(entry.getKey(), list);
                } else {
                    params.put(entry.getKey(), value.toString());
                }
            });
        }

        // 根据意图调用对应业务方法
        Map<String, Object> result;
        switch (intent) {
            case "ROUTE_PLAN":
                result = handleRoutePlan(naturalLanguage, params);
                break;
            case "SCHEDULE_CHECK":
                result = handleScheduleCheck(naturalLanguage);
                if (params.containsKey("timeRange")) {
                    result.put("timeRange", params.get("timeRange"));
                }
                if (params.containsKey("attendees")) {
                    result.put("attendees", params.get("attendees"));
                }
                break;
            case "MEETING_QUERY":
            default:
                result = handleMeetingQuery(naturalLanguage);
                if (params.containsKey("date")) {
                    result.put("date", params.get("date"));
                }
                if (params.containsKey("capacity")) {
                    result.put("capacity", params.get("capacity"));
                }
                if (params.containsKey("equipment")) {
                    result.put("equipment", params.get("equipment"));
                }
                break;
        }

        // 统一包装：确保前端能识别 intent 和 aiParsed
        Map<String, Object> wrapped = new HashMap<>(result);
        wrapped.put("intent", intent);
        wrapped.put("aiParsed", aiResult);

        // 路线规划如果没有坐标数组，补一段 Mock 路径让地图能显示
        if ("ROUTE_PLAN".equals(intent)) {
            if (!wrapped.containsKey("path") || wrapped.get("path") == null) {
                List<List<Double>> mockPath = generateMockPath();
                wrapped.put("path", mockPath);
                wrapped.putIfAbsent("startPoint", mockPath.get(0));
                wrapped.putIfAbsent("endPoint", mockPath.get(mockPath.size() - 1));
            }
        }

        return wrapped;
    }

    private List<List<Double>> generateMockPath() {
        List<List<Double>> path = new ArrayList<>();
        path.add(List.of(116.321, 39.894));
        path.add(List.of(116.35, 39.85));
        path.add(List.of(116.38, 39.78));
        path.add(List.of(116.40, 39.65));
        path.add(List.of(116.41, 39.55));
        path.add(List.of(116.412, 39.509));
        return path;
    }

    // ==================== 会议室查询 ====================
    private Map<String, Object> handleMeetingQuery(String naturalLanguage) {
        String systemPrompt = """
            你是企业会议室查询助手。分析用户需求，提取以下信息并返回JSON：
            {
                "intent": "query",
                "date": "日期描述（如today/tomorrow/2026-07-11）",
                "timeRange": "时间段（如09:00 to 11:00）",
                "capacity": 人数,
                "equipment": ["设备1", "设备2"],
                "reasoning": "你的分析过程"
            }
            如果信息缺失，给出合理默认值。只返回JSON，不要其他文字。
            """;

        String aiJson = deepSeekService.chat(systemPrompt, naturalLanguage);
        JsonNode aiResult = parseJson(aiJson);

        // Parse date: default to tomorrow
        LocalDateTime startTime;
        String dateStr = aiResult.path("date").asText("tomorrow");
        if ("today".equalsIgnoreCase(dateStr)) {
            startTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if ("tomorrow".equalsIgnoreCase(dateStr)) {
            startTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else {
            try {
                startTime = LocalDateTime.parse(dateStr + "T00:00:00");
            } catch (Exception e) {
                startTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            }
        }

        // Parse time range: default 09:00-11:00
        String timeRangeStr = aiResult.path("timeRange").asText("09:00 to 11:00");
        // Handle both "17:20 to 18:50" and "17:20-18:50" formats
        String[] parts = timeRangeStr.contains(" to ") ? timeRangeStr.split(" to ") : timeRangeStr.split("-");
        String startTimeStr = parts.length >= 1 ? parts[0].trim() : "09:00";
        String endTimeStr = parts.length >= 2 ? parts[1].trim() : "11:00";
        if (!startTimeStr.contains(":")) startTimeStr = "09:00";
        if (!endTimeStr.contains(":")) endTimeStr = "11:00";

        LocalDateTime queryStart = LocalDateTime.parse(
            startTime.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T" + startTimeStr + ":00");
        LocalDateTime queryEnd = LocalDateTime.parse(
            startTime.format(DateTimeFormatter.ISO_LOCAL_DATE) + "T" + endTimeStr + ":00");

        int capacity = aiResult.path("capacity").asInt(5);

        log.info("Meeting query: date={}, time={} to {}, capacity={}", startTime.toLocalDate(), startTimeStr, endTimeStr, capacity);

        // Query real DB
        List<Map<String, Object>> rooms = meetingRoomService.queryRoomsWithStatus(queryStart, queryEnd, capacity);

        Map<String, Object> data = new HashMap<>();
        data.put("rooms", rooms);
        data.put("total", rooms.size());
        data.put("aiParsed", aiResult);
        data.put("source", "database");
        return data;
    }

    // ==================== 日程冲突 ====================
    private Map<String, Object> handleScheduleCheck(String naturalLanguage) {
        String systemPrompt = """
            你是日程管理助手。分析会议安排请求，检查冲突并返回JSON：
            {
                "hasConflict": true/false,
                "conflictReason": "冲突原因或'无冲突'",
                "suggestedSlots": ["替代时间段"],
                "optimizationTip": "优化建议"
            }
            只返回JSON，不要其他文字。
            """;

        String aiJson = deepSeekService.chat(systemPrompt, naturalLanguage);
        JsonNode aiResult = parseJson(aiJson);

        Map<String, Object> data = new HashMap<>();
        data.put("hasConflict", aiResult.path("hasConflict").asBoolean(false));
        data.put("conflictReason", aiResult.path("conflictReason").asText("未检测到冲突"));
        data.put("suggestedTime", toList(aiResult.path("suggestedSlots")));
        data.put("optimizationTip", aiResult.path("optimizationTip").asText(""));
        data.put("aiAnalysis", aiResult);
        data.put("source", "deepseek");
        return data;
    }

    // ==================== 路线规划 ====================
    private Map<String, Object> handleRoutePlan(String naturalLanguage, Map<String, Object> params) {
        String from = params != null && params.get("from") != null
                ? params.get("from").toString() : "当前位置";
        String to = params != null && params.get("to") != null
                ? params.get("to").toString() : "目的地";

        Map<String, Object> rawResult;
        try {
            rawResult = amapService.planDrivingRoute(from, to, 0);
        } catch (Exception e) {
            log.warn("高德路线规划失败，使用Mock数据", e);
            rawResult = null;
        }

        // 用 HashMap 包装，防止 amapService 返回不可变 Map 导致 put 报错
        Map<String, Object> amapResult = rawResult != null ? new HashMap<>(rawResult) : new HashMap<>();

        if (rawResult == null) {
            amapResult.put("distance", "12.5km");
            amapResult.put("duration", "35分钟");
            amapResult.put("trafficStatus", "畅通");
            amapResult.put("steps", List.of(
                    Map.of("instruction", "从" + from + "出发", "distance", "500m"),
                    Map.of("instruction", "进入主干道", "distance", "3.2km"),
                    Map.of("instruction", "到达" + to, "distance", "200m")
            ));
            amapResult.put("source", "mock");
        }

        // DeepSeek 润色
//        try {
//            String systemPrompt = """
//                你是智能导航助手。根据路线数据生成简洁的导航总结，返回JSON：
//                {
//                    "summary": "一句话总结",
//                    "trafficAdvice": "交通建议",
//                    "alternative": "备选方案建议"
//                }
//                只返回JSON。
//                """;
//
//            String userContent = String.format("从%s到%s，距离%s，预计%s，路况%s",
//                    from, to,
//                    amapResult.getOrDefault("distance", "未知"),
//                    amapResult.getOrDefault("duration", "未知"),
//                    amapResult.getOrDefault("trafficStatus", "未知"));
//
//            String aiJson = deepSeekService.chat(systemPrompt, userContent);
//            JsonNode aiResult = parseJson(aiJson);
//
//            amapResult.put("summary", aiResult.path("summary").asText(""));
//            amapResult.put("trafficAdvice", aiResult.path("trafficAdvice").asText(""));
//            amapResult.put("alternative", aiResult.path("alternative").asText(""));
//            if (!amapResult.containsKey("source")) {
//                amapResult.put("source", "amap+deepseek");
//            }
//        } catch (Exception e) {
//            log.warn("DeepSeek润色失败，返回原始路线数据", e);
//        }

        amapResult.put("from", from);
        amapResult.put("to", to);
        amapResult.put("mode", params != null && params.get("mode") != null ? params.get("mode") : "driving");

        return amapResult;
    }

    // ==================== 工具方法 ====================
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("JSON解析失败: {}", json);
            return objectMapper.createObjectNode();
        }
    }

    private List<String> toList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode n : node) list.add(n.asText());
        return list;
    }

    private Map<String, Object> fallbackMock(String toolType) {
        String upper = toolType != null ? toolType.toUpperCase() : "MEETING_QUERY";
        return switch (upper) {
            case "MEETING_QUERY", "AI", "NLP" -> {
                Map<String, Object> m = new HashMap<>();
                m.put("rooms", List.of());
                m.put("total", 0);
                m.put("source", "mock");
                m.put("intent", "MEETING_QUERY");
                yield m;
            }
            case "SCHEDULE_CHECK" -> {
                Map<String, Object> m = new HashMap<>();
                m.put("hasConflict", false);
                m.put("source", "mock");
                m.put("intent", "SCHEDULE_CHECK");
                yield m;
            }
            case "ROUTE_PLAN" -> {
                Map<String, Object> m = new HashMap<>();
                m.put("distance", "未知");
                m.put("duration", "未知");
                m.put("source", "mock");
                m.put("intent", "ROUTE_PLAN");
                yield m;
            }
            default -> Map.of("source", "mock");
        };
    }
}