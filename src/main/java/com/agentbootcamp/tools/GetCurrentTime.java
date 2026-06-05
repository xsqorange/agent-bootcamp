package com.agentbootcamp.tools;

import com.agentbootcamp.Tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 工具 1:返回当前时间 / Tool 1: returns current time
 *
 * Day 1 验收之一:让模型知道"现在几点"
 * Day 1 acceptance: helps the model know "what time is it"
 */
public class GetCurrentTime implements Tool {

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "返回当前时间(ISO 8601 格式,带时区)/ Returns the current time in ISO 8601 format with timezone.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "timezone", Map.of(
                    "type", "string",
                    "description", "IANA 时区名,例如 'Asia/Shanghai' / IANA timezone, e.g. 'Asia/Shanghai'. 可选,默认 UTC."
                )
            ),
            "required", List.of()
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String tz = (String) args.getOrDefault("timezone", "UTC");
        try {
            return ZonedDateTime.now(java.time.ZoneId.of(tz))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return "无效时区: " + tz + " (请用 IANA 名字,例如 'Asia/Shanghai')";
        }
    }
}
