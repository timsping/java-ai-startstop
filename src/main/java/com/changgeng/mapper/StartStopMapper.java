package com.changgeng.mapper;

import com.changgeng.model.StartStopQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StartStopMapper {

    List<Map> selectAllEvent(@Param("param") StartStopQueryDTO startStopQueryDTO);

    String selectStartMode(@Param("param") Map map);

    List<Map> startStopDetails(@Param("resultId") String resultId);

    List<Map> materialDetails(@Param("resultId") String resultId);

    String selectLastEvent(@Param("resultId") String resultId);

    List<Map> startStopRecord(@Param("param") StartStopQueryDTO startStopQueryDTO);
}
