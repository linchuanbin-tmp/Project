package com.agent.tool.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("meeting_schedule")
public class MeetingSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private String booker;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String topic;
    private Integer status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}