package com.changgeng.controller;

import com.alibaba.excel.util.ListUtils;
import com.changgeng.common.result.Result;
import com.changgeng.service.DeerFlowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class DeerFlowTestController {

    private final DeerFlowService deerFlowService;

    public DeerFlowTestController(DeerFlowService deerFlowService) {
        this.deerFlowService = deerFlowService;
    }

    @PostMapping("/test/deerflow/chat")
    public Result<String> testChat(@RequestBody Map param) {
        String question = (String) param.get("content");
        log.error("content {}" , question);
        if("1".equals(question)){
            return Result.error("未找到AI回复");
        }
        // 1. 创建会话
        String result = deerFlowService.getOrCreateThreadId("测试")
                .flatMap(threadResp -> {
                    String tid = threadResp.toString();
                    log.error("会话id {}", tid);
                    // 2. 提问，拿到回答
                    return deerFlowService.askQuestion(tid, question);
                }).block();
        return Result.success(textDeal(result));
    }

    public static void main(String[] args) {
        String result = "\\\"#2机组启动过程总结（2025-12-01 至 2025-12-26）\\\\n\\\\n**总体结论**：统计范围内 #2机组共完成 **2 次一拖一启动**，均为**热态启动模式**，事件全部闭环，闭环率 100%。其中 12-02 启动过程顺利、未采集到异常事件；12-26 启动（事件窗口自 12-18 起）有效启动段明显拉长、四项物耗## 一、事件概况高于上次（+6.5%～+8.5%），并采集到 3 项异常（均已闭环）。期间穿插 1 次停机（12-02 21:27 停机，停备至 12-18，约 366.9 h），该停机使缸温充分冷却，是第二次启动冲转判定为冷态的直接背景。\\\\n\\\\n---\\\\n\\\\n## 一、启动事件清单\\\\n\\\\n| 事件ID | 启动类型 | 开始（北京） | 结束（北京） | 总耗时 | 闭环 |\\\\n| --- | --- | --- | --- | --- | --- |\\\\n| SS-20251202-001 | 热态启动模式 | 12-02 09:02 | 12-02 20:51 | 709 min（约11.8 h） | 是 |\\\\n| SS-20251226-001 | 热态启动模式 | 12-18 14:27 | 12-26 21:23 | 199.0 h（含停备期） | 是 |\\\\n\\\\n> SS-20251226-001 事件窗口含 12-18～12-25 停备维持段，**实际启动操作集中在 12-26 当日**，下文按\\\\\\\"有效启动段\\\\\\\"口径另行统计。\\\\n\\\\n## 二、启动一（SS-20251202-001）过程还原\\\\n\\\\n| 时刻（北京） | 节点 |\\\\n| --- | --- |\\\\n| 09:02 | 启动准备：凝结水、循环水、闭式水、EH油系统投入，余热锅炉上水，盘车正常，投轴封抽真空 |\\\\n| 09:02 | 燃机升速至盘车脱扣 |\\\\n| 09:15 | 燃机全速；同步进入燃机并网带初负荷 |\\\\n| 09:32 / 09:58 / 10:39 | 高压 / 中压 / 低压并汽准备完成 |\\\\n| 10:03 | 燃机并网带初负荷完成 |\\\\n| 10:18 | 主汽门全开 |\\\\n| 10:30 | 汽机冲转（判定：**温态冲转**，高压缸温度1≥120℃） |\\\\n| 10:51–11:03 | 汽机升速（过一阶/二阶临界、顶轴油切出、定速） |\\\\n| 11:08 | 机端升压、汽机发电机升负荷（汽机侧并网完成） |\\\\n| 20:51 | 启动事件闭环 |\\\\n\\\\n- 关键口径：盘车脱扣→汽机并网 **126 min**。\\\\n- 物耗：水 1160.00 m³、煤（燃气）9400.00 Nm³、油 172.00 L、厂用电 9.20 MWh。\\\\n- 异常：本事件**未采集到异常事件**。\\\\n\\\\n## 三、启动二（SS-20251226-001）过程还原（有效启动段）\\\\n\\\\n| 时刻（北京，12-26） | 节点 |\\\\n| --- | --- |\\\\n| 05:02 | 启动准备过程开始（循环水、EH油、闭式水、盘车等停备期已维持） |\\\\n| 05:59–06:30 | 投轴封抽真空 |\\\\n| 08:24 | 燃机升速至盘车脱扣 |\\\\n| 08:45 | 燃机全速；同步进入燃机并网带初负荷 |\\\\n| 09:29 | 中压并汽准备完成 |\\\\n| 09:42 | 冷却塔风机运行 |\\\\n| 10:31 | 燃机并网带初负荷完成 |\\\\n| 10:52 | 低压并汽准备完成 |\\\\n| 11:11 | 汽机冲转（判定：**冷态冲转**，高压缸温度1<120℃，与停备约15天相符） |\\\\n| 11:42–11:54 | 汽机升速 |\\\\n| 12:31–12:32 | 机端升压、汽机发电机升负荷、CCS投入 |\\\\n| 21:23 | CCS投入完成，启动事件闭环 |\\\\n\\\\n> 注：主汽门全开记录时间为 12-25 09:48，早于实际启动动作，疑为采集异常，未纳入时间轴。\\\\n\\\\n- 关键口径：盘车脱扣→汽机并网 **248 min（约4.1 h）**；有效启动准备段 05:02→12:32 共 450 min。\\\\n\\\\n## 四、两次启动对比（同口径：盘车脱扣→汽机并网）\\\\n\\\\n| 阶段 | SS-20251202-001 | SS-20251226-001 | 差值 |\\\\n| --- | --- | --- | --- |\\\\n| 盘车脱扣→燃机全速 | 13 min | 21 min | +8 min |\\\\n| 燃机并网带初负荷 | 48 min | 106 min | +58 min |\\\\n| 全速完成→汽机冲转 | 15+12 min | 40 min | +13 min |\\\\n| 汽机冲转→定速 | 33 min | 43 min | +10 min |\\\\n| 定速→机端升压 | 5 min | 37 min | +32 min |\\\\n| **合计** | **126 min** | **248 min** | **+122 min** |\\\\n\\\\n**物耗对比**（SS-20251226-001 vs 上次）：\\\\n\\\\n| 物料 | 本次 | 上次 | 偏差 | 偏差率 |\\\\n| --- | --- | --- | --- | --- |\\\\n| 水 | 1240.00 m³ | 1160.00 m³ | +80.00 m³ | +6.9% |\\\\n| 煤（燃气） | 10200.00 Nm³ | 9400.00 Nm³ | +800.00 Nm³ | +8.5% |\\\\n| 油 | 184.00 L | 172.00 L | +12.00 L | +7.0% |\\\\n| 厂用电 | 9.80 MWh | 9.20 MWh | +0.60 MWh | +6.5% |\\\\n\\\\n分阶段看，五个阶段物耗全部高于上次，其中燃机启动段燃气 3400.00 Nm³（上次 3100.00）与汽机冲转升速段燃气 2500.00 Nm³（上次 2300.00）增量最大，与耗时拉长段一致。\\\\n\\\\n## 五、异常概况（SS-20251226-001，均已闭环）\\\\n\\\\n| 异常测点 | 类型 | 数值 | 时间（北京） | 持续 | 所处阶段 |\\\\n| --- | --- | --- | --- | --- | --- |\\\\n| 轴封电加热器出口温度 | 超下限 | 243.15 ℃ | 12-25 22:11–12-26 05:21 | 430 min | 轴封投运前的低缸温阶段 |\\\\n| #2汽机中压旁路阀后蒸汽温度2 | 超上限 | 132.66 ℃ | 09:22–10:29 | 67 min | 燃机转速达标→并网带初负荷 |\\\\n| #2汽机中压旁路阀后蒸汽温度1 | 超上限 | 130.79 ℃ | 09:23–10:29 | 66 min | 同上 |\\\\n\\\\n## 六、总体评价\\\\n\\\\n两次启动均为热态启动模式且全部闭环，操作链路完整（启动准备→燃机启动并汽→燃机并网→汽机冲转升速→汽机发电机并网→CCS投入）。12-02 启动各阶段衔接紧凑，无异常采集。12-26 启动因停备约 15 天转为冷态冲转，燃机并网带初负荷段和定速后升压环节合计拖长约 90 min，是总耗时偏长的主要环节，对应物耗四项同比上升 6.5%～8.5%；轴封电加热器出口温度超下限发生在投轴封前升温阶段，中压旁路阀后双测点超上限集中在并网带初负荷低负荷段，三项异常均已闭环，建议后续冷态启动关注轴封投入提前量与旁路减温投入时机。\\\\n\\\\n如需下钻，可继续问：两次启动某阶段的曲线对比、12-26 启动物耗偏高的归因分析、或本次与历史最优记录的对比。\\\"";
        System.out.println(textDeal(result));
    }


    public static String textDeal(String result){
       /* if(true){
            return result;
        }*/
        List<String> filterTextSubNext = ListUtils.newArrayList("## 一、事件概况","## 一、启动概览");
        List<String> filterTextSubLast = ListUtils.newArrayList("如需","继续","另注","需要","后续");
        List<String> filterTextRep = ListUtils.newArrayList("结论先行");
        for (String s : filterTextSubNext) {
            int indexOf = result.indexOf(s);
            if (indexOf != -1) {
                result = result.substring(indexOf);
            }
        }
        for (String s : filterTextSubLast) {
            int indexOf = result.indexOf(s);
            if (indexOf != -1) {
                result = result.substring(1,indexOf);
            }
        }
        for (String s : filterTextRep) {
            result = result.replaceAll(s, "");
        }
        return result.replaceAll("^(?!\")", "\"").replaceAll("(?<!\")$", "\"");
    }
}
