package com.changgeng.service;

import com.alibaba.fastjson.JSON;
import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import com.changgeng.mapper.StartStopMapper;
import com.changgeng.model.StartStopQueryDTO;
import com.changgeng.pojo.SourceRecord;
import com.changgeng.tree.TreeBuildUtil;
import com.changgeng.tree.TreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StartStopService {
    @Resource
    private StartStopMapper startStopMapper;
    @Resource
    private DamExtClient damExtClient;


    public Result startStopStatic(StartStopQueryDTO startStopStatic) {
        log.info("startStopStatic param {}" , JSON.toJSONString(startStopStatic));
        //查询指定机组下整体事件
        List<Map> allEvent = damExtClient.getAllEvent(startStopStatic.getNodeId());
        if(CollectionUtils.isEmpty(allEvent)){
            return Result.error("未查询到相关的启停记录");
        }
        log.info("查询到事件数据 {}" , JSON.toJSONString(allEvent));
        List<Integer> eventIds = allEvent.stream().map(e -> Integer.parseInt(e.get("eventId").toString())).collect(Collectors.toList());
        startStopStatic.setEventIds(eventIds);
        List<Map> list = startStopMapper.selectAllEvent(startStopStatic);
        //获取冷态/温态/热态
        list = list.stream().peek(map->map.put("startMode" , startStopMapper.selectStartMode(map))).collect(Collectors.toList());
        return Result.success(list);
    }

    public Result startStopDetails(StartStopQueryDTO startStopQueryDTO) {
        log.info("startStopDetails param {}" , JSON.toJSONString(startStopQueryDTO));
        String resultId = startStopQueryDTO.getResultId();
        List<Map> list = startStopMapper.startStopDetails(resultId);
        list = list.stream().peek(map->map.put("childrenEvent" , startStopMapper.startStopDetails(map.get("result_id").toString()))).collect(Collectors.toList());
        return Result.success(list);
    }

    public Result materialDetails(StartStopQueryDTO startStopQueryDTO) {
        Map<String , Object> result = new LinkedHashMap<>();
        log.info("materialDetails param {}" , JSON.toJSONString(startStopQueryDTO));
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
        log.info("startStopRecord param {}" , JSON.toJSONString(startStopQueryDTO));
        List<Map> allEvent = damExtClient.getAllEvent(startStopQueryDTO.getNodeId());
        if(CollectionUtils.isEmpty(allEvent)){
            return Result.error("未查询到相关的启停记录");
        }
        List<Integer> eventIds = allEvent.stream().map(e -> Integer.parseInt(e.get("eventId").toString())).collect(Collectors.toList());
        startStopQueryDTO.setEventIds(eventIds);
        return Result.success(startStopMapper.startStopRecord(startStopQueryDTO));
    }

    public Result stageRecord(StartStopQueryDTO startStopQueryDTO) {
        log.info("stageRecord param {}" , JSON.toJSONString(startStopQueryDTO));
        List<Map> allEvent = damExtClient.getAllEvent(startStopQueryDTO.getNodeId());
        if(!CollectionUtils.isEmpty(allEvent)){
            for (Map map : allEvent) {
                String eventName = map.get("eventName").toString();
                if(eventName.contains(startStopQueryDTO.getEventName())){
                    Integer eventId = (Integer) map.get("eventId");
                    List<Map> allEventList = damExtClient.getAllEventList(startStopQueryDTO.getNodeId(), eventId);
                    //获取正在进行中的整体事件
                    Map<String, Map> selectCurrentStartStop = startStopMapper.selectCurrentStartStop(eventId);
                    List<SourceRecord> sourceRecordList = JSON.parseArray(JSON.toJSONString(allEventList), SourceRecord.class);
                    List<TreeNode> treeNodes = TreeBuildUtil.buildTree(sourceRecordList);
                    Map<String,Map> defaultMap = new HashMap(){{
                        put("eventStatus" ,"未开始");
                        put("minute" ,0d);
                    }};
                    fillTreeNodeStartData(treeNodes, selectCurrentStartStop, defaultMap);
                    return Result.success(treeNodes);
                }
            }
        }
        return Result.error("操作失败");
    }

    private void fillTreeNodeStartData(List<TreeNode> treeNodes,
                                       Map<String, Map> currentStartStopMap,
                                       Map defaultMap) {
        if (CollectionUtils.isEmpty(treeNodes)) {
            return;
        }
        for (TreeNode treeNode : treeNodes) {
            Map<String, Object> nodeData = currentStartStopMap.getOrDefault(treeNode.getCode(), defaultMap);
            treeNode.setStartDatas(getCurrentStartMode(nodeData));
            fillTreeNodeStartData(treeNode.getChildren(), currentStartStopMap, defaultMap);
        }
    }

    private Map getCurrentStartMode(Map data) {
        String eventCode = data.get("event_code").toString();
        Date startTime = (Date) data.get("start_time");
        Map currentStartMode = startStopMapper.getCurrentStartMode(eventCode, startTime);
        if(!CollectionUtils.isEmpty(currentStartMode)){
            data.put("currentStartMode",currentStartMode.get("event_name"));
            Integer eventId = (Integer) currentStartMode.get("event_id");
            List<Map> nodes = damExtClient.getNode(eventId);
            String currentStartModeDesc = ((Map) nodes.get(0).get("n")).get("公式说明").toString();
            data.put("currentStartModeDesc" , currentStartModeDesc);
        }
        return data;
    }
}
