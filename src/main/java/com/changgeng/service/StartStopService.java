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
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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


    public Result startStopCondition(StartStopQueryDTO startStopQueryDTO) {
        log.info("startStopCondition param {}" , JSON.toJSONString(startStopQueryDTO));
        Integer nodeId = startStopQueryDTO.getNodeId();
        String condition = startStopQueryDTO.getCondition();
        List<Map> startStopCondition = startStopMapper.startStopCondition(nodeId, condition);
        if(CollectionUtils.isEmpty(startStopCondition)){
            return Result.error("未查询到相关的判定记录");
        }
        return Result.success(startStopCondition);
    }

    private void fillTreeNodeStartData(List<TreeNode> treeNodes,
                                       Map<String, Map> currentStartStopMap,
                                       Map defaultMap) {
        if (CollectionUtils.isEmpty(treeNodes)) {
            return;
        }
        for (TreeNode treeNode : treeNodes) {
            Map<String, Object> nodeData = currentStartStopMap.getOrDefault(treeNode.getCode(), defaultMap);
            if(nodeData.containsKey("event_type") && nodeData.get("event_type").equals("1")){
                treeNode.setStartDatas(getCurrentStartMode(nodeData));
            }
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

    public Result eventStatus(String resultId) {
        log.info("eventStatus param resultId: {}", resultId);
        List<Map> list = startStopMapper.eventStatus(resultId);
        if (CollectionUtils.isEmpty(list)) {
            return Result.success(null);
        }

        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        list.forEach(item -> {
            item.put("children", new ArrayList<>());
            nodeMap.put(item.get("result_id").toString(), item);
        });

        Map<String, Object> root = null;
        for (Map<String, Object> node : nodeMap.values()) {
            Object pid = node.get("parent_result_id");
            Map<String, Object> parent = pid != null ? nodeMap.get(pid.toString()) : null;
            if (parent != null && !node.get("result_id").toString().equalsIgnoreCase(resultId)) {
                ((List) parent.get("children")).add(node);
            } else if (root == null) {
                root = node;
            }
        }

        nodeMap.values().parallelStream().forEach(node -> {
            if (node.get("eventId") != null) {
                try {
                    List<Map> nodes = damExtClient.getNode(Integer.parseInt(node.get("eventId").toString()));
                    if (!CollectionUtils.isEmpty(nodes) && nodes.get(0).get("n") instanceof Map) {
                        node.put("formulaDesc", ((Map) nodes.get(0).get("n")).get("公式说明"));
                    }
                } catch (Exception ignored) {}
                node.remove("children");
            }
            node.remove("result_id");
            node.remove("parent_result_id");
            node.remove("eventId");
        });

        return Result.success(root);
    }

    public Result standardChart(StartStopQueryDTO startStopQueryDTO) {
        log.info("standardChart param {}" , JSON.toJSONString(startStopQueryDTO));
        String startMode = startStopQueryDTO.getStartMode();
        String groupName = null;
        if(StringUtils.isEmpty(startMode)){
            return Result.error("标准曲线不存在");
        }else {
            if(startMode.equals("热态启动模式")){
                groupName = "锅炉热态启动曲线";
            }
            if(startMode.equals("冷态启动模式")){
                groupName = "锅炉冷态启动曲线";
            }
            if(startMode.equals("停机模式")){
                groupName = "停机曲线";
            }
        }
        List<Map> list = startStopMapper.selectStandardChart(groupName);
        if(CollectionUtils.isEmpty(list)){
            return Result.error("标准曲线不存在");
        }
        Map<Object, List<Map>> standardNameGroup = list.stream()
                .collect(Collectors.groupingBy(
                        e -> e.get("standard_name"),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                originMap -> {
                                    Map copyMap = new HashMap<>(originMap);
                                    copyMap.remove("standard_name");
                                    copyMap.put("realtime_value",(Double)copyMap.get("standard_value")- ThreadLocalRandom.current().nextDouble(-10, 10));
                                    return copyMap;
                                },
                                Collectors.toList()
                        )
                ));

        return Result.success(standardNameGroup);
    }
}
