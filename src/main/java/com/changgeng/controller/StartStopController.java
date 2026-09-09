package com.changgeng.controller;

import com.changgeng.common.result.Result;
import com.changgeng.service.StarStopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/benchmark")
@Slf4j
public class StartStopController {

    @Resource
    private StarStopService startStopService;
}
