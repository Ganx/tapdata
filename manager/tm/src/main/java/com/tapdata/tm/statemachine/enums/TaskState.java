/**
 * @title: SubTaskState
 * @description:
 * @author lk
 * @date 2021/11/24
 */
package com.tapdata.tm.statemachine.enums;

import static com.tapdata.tm.commons.task.dto.TaskDto.*;
import java.util.HashMap;
import java.util.Map;

public enum TaskState {

	/**
	 * 编辑中
	 **/
	EDIT(STATUS_EDIT),
	/**
	 * 调度中
	 **/
	SCHEDULING(STATUS_SCHEDULING),
	/**
	 * 调度失败
	 **/
	SCHEDULING_FAILED(STATUS_SCHEDULE_FAILED),
	/**
	 * 待运行
	 **/
	WAITING_RUN(STATUS_WAIT_RUN),
	/**
	 * 运行中
	 **/
	RUNNING(STATUS_RUNNING),
	/**
	 * 停止中
	 **/
	STOPPING(STATUS_STOPPING),
	/**
	 * 错误
	 **/
	ERROR(STATUS_ERROR),
	/**
	 * 已停止
	 **/
	STOPPED(STATUS_STOP),
	/**
	 * 已完成
	 **/
	DONE(STATUS_COMPLETE);

	private String name;

	TaskState(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	private static final Map<String, TaskState> map = new HashMap<>();

	static {
		for (TaskState value : TaskState.values()) {
			map.put(value.getName(),value);
		}
	}

	public static TaskState getState(String name){
		return map.get(name);
	}

	@Override
	public String toString() {
		return getName();
	}
}
