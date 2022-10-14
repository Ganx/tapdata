package com.tapdata.tm.alarm.service;

import com.tapdata.tm.alarm.dto.AlarmListInfoVo;
import com.tapdata.tm.alarm.dto.AlarmListReqDto;
import com.tapdata.tm.alarm.dto.TaskAlarmInfoVo;
import com.tapdata.tm.alarm.entity.AlarmInfo;
import com.tapdata.tm.alarm.scheduler.Rule;
import com.tapdata.tm.base.dto.Page;
import com.tapdata.tm.commons.task.constant.AlarmKeyEnum;
import com.tapdata.tm.commons.task.constant.NotifyEnum;
import com.tapdata.tm.commons.task.dto.Milestone;
import com.tapdata.tm.commons.task.dto.TaskDto;
import com.tapdata.tm.commons.task.dto.alarm.AlarmRuleDto;
import com.tapdata.tm.config.security.UserDetail;
import org.bson.Document;

import java.util.List;
import java.util.Map;

public interface AlarmService {
    void save(AlarmInfo info);

    boolean checkOpen(String taskId, String nodeId, AlarmKeyEnum key, NotifyEnum notityType);

    Map<String, List<AlarmRuleDto>> getAlarmRuleDtos(TaskDto taskDto);
    List<Rule> findAllRuleWithMoreInfo(String taskId);

    void notifyAlarm();

    void close(String[] ids, UserDetail userDetail);

    Page<AlarmListInfoVo> list(String status,
                               Long start,
                               Long end,
                               String keyword,
                               Integer page,
                               Integer size,
                               UserDetail userDetail);

    TaskAlarmInfoVo listByTask(AlarmListReqDto dto);

    List<AlarmInfo> find(String taskId, String nodeId, AlarmKeyEnum key);

    void closeWhenTaskRunning(String taskId);

    void checkFullAndCdcEvent(String taskId);
}
