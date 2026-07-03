package com.agent.tool.service.impl;

import com.agent.tool.entity.MeetingSchedule;
import com.agent.tool.mapper.MeetingScheduleMapper;
import com.agent.tool.service.MeetingScheduleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class MeetingScheduleServiceImpl extends ServiceImpl<MeetingScheduleMapper, MeetingSchedule> implements MeetingScheduleService {
}
