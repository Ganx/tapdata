package io.tapdata.zoho.entity.webHook.doMain;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.simplify.TapSimplify;
import io.tapdata.zoho.entity.webHook.EventBaseEntity;

import java.util.Map;

public class UpdateEventEntity extends EventBaseEntity<UpdateEventEntity> {
    private Map<String,Object> prevState;
    public static UpdateEventEntity create(){
        return new UpdateEventEntity();
    }
    public UpdateEventEntity prevState(Map<String,Object> prevState){
        this.prevState = prevState;
        return this;
    }
    public Map<String,Object> prevState(){
        return this.prevState;
    }

    @Override
    protected UpdateEventEntity event(Map<String, Object> issueEventData) {
        return BeanUtil.mapToBean(issueEventData,UpdateEventEntity.class,true, CopyOptions.create().ignoreError());
    }

    @Override
    public TapEvent outputTapEvent(String table) {
        return TapSimplify.updateDMLEvent(this.prevState, this.payload(), table).referenceTime(System.currentTimeMillis());
    }

    public Map<String, Object> getPrevState() {
        return prevState;
    }

    public void setPrevState(Map<String, Object> prevState) {
        this.prevState = prevState;
    }
}
