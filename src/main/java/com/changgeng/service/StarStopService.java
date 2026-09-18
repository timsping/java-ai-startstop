package com.changgeng.service;

import com.alibaba.fastjson.JSON;
import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import com.changgeng.mapper.StartStopMapper;
import com.changgeng.model.StartStopQueryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StarStopService {
    @Resource
    private StartStopMapper startStopMapper;
    @Resource
    private DamExtClient damExtClient;


    public Result startStopStatic(StartStopQueryDTO startStopStatic) {
        log.error("startStopStatic param {}" , JSON.toJSONString(startStopStatic));
        //查询指定机组下整体事件
        List<Map> allEvent = damExtClient.getAllEvent(startStopStatic.getNodeId());
        if(CollectionUtils.isEmpty(allEvent)){
            return Result.error("未查询到相关的启停记录");
        }
        log.error("查询到事件数据 {}" , JSON.toJSONString(allEvent));
        List<Integer> eventIds = allEvent.stream().map(e -> Integer.parseInt(e.get("eventId").toString())).collect(Collectors.toList());
        startStopStatic.setEventIds(eventIds);
        List<Map> list = startStopMapper.selectAllEvent(startStopStatic);
        //获取冷态/温态/热态
        list = list.stream().peek(map->map.put("startMode" , startStopMapper.selectStartMode(map))).collect(Collectors.toList());
        return Result.success(list);
    }

    public Result startStopDetails(StartStopQueryDTO startStopQueryDTO) {
        log.error("startStopDetails param {}" , JSON.toJSONString(startStopQueryDTO));
        String resultId = startStopQueryDTO.getResultId();
        List<Map> list = startStopMapper.startStopDetails(resultId);
        list = list.stream().peek(map->map.put("childrenEvent" , startStopMapper.startStopDetails(map.get("result_id").toString()))).collect(Collectors.toList());
        return Result.success(list);
    }

    public Result materialDetails(StartStopQueryDTO startStopQueryDTO) {
        Map<String , Object> result = new LinkedHashMap<>();
        log.error("materialDetails param {}" , JSON.toJSONString(startStopQueryDTO));
        //本次物料信息
        List<Map> currentMaterialList = startStopMapper.materialDetails(startStopQueryDTO.getResultId());
        result.put("currentMaterialList" , currentMaterialList);
        //上次物料信息
        String lastRecordId =  startStopMapper.selectLastEvent(startStopQueryDTO.getResultId());
        if(lastRecordId != null){
            List<Map> lastMaterialList = startStopMapper.materialDetails(lastRecordId);
            result.put("lastMaterialList" , lastMaterialList);
        }
        return Result.success(result);
    }

    public Result startStopRecord(StartStopQueryDTO startStopQueryDTO) {
        log.error("startStopRecord param {}" , JSON.toJSONString(startStopQueryDTO));
        List<Map> allEvent = damExtClient.getAllEvent(startStopQueryDTO.getNodeId());
        if(CollectionUtils.isEmpty(allEvent)){
            return Result.error("未查询到相关的启停记录");
        }
        List<Integer> eventIds = allEvent.stream().map(e -> Integer.parseInt(e.get("eventId").toString())).collect(Collectors.toList());
        startStopQueryDTO.setEventIds(eventIds);
        return Result.success(startStopMapper.startStopRecord(startStopQueryDTO));
    }
}
