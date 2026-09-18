package com.changgeng.controller;

import com.changgeng.common.result.Result;
import com.changgeng.model.StartStopQueryDTO;
import com.changgeng.service.StarStopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/startStop")
@Slf4j
public class StartStopController {

    @Resource
    private StarStopService startStopService;

    /**
     * 统计指定时间范围内启动次数、停机次数，并补充累计运行时长、停机时长
     * @return
     */
    @PostMapping("/statistics")
    public Result startStopStatic(@RequestBody StartStopQueryDTO startStopStatic){
        return startStopService.startStopStatic(startStopStatic);
    }

    /**
     * 启停记录详情
     * @return
     */
    @PostMapping("/details")
    public Result startStopDetails(@RequestBody StartStopQueryDTO startStopQueryDTO){
        return startStopService.startStopDetails(startStopQueryDTO);
    }


    /**
     * 启停物料查询
     * @return
     */
    @PostMapping("/material/details")
    public Result materialDetails(@RequestBody StartStopQueryDTO startStopQueryDTO){
        return startStopService.materialDetails(startStopQueryDTO);
    }

    /**
     * 获取最近几次启停记录
     * @return
     */
    @PostMapping("/record")
    public Result startStopRecord(@RequestBody StartStopQueryDTO startStopQueryDTO){
        return startStopService.startStopRecord(startStopQueryDTO);
    }
}

