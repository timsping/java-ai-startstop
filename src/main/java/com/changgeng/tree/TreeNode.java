package com.changgeng.tree;

import lombok.Data;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class TreeNode {
    /** 节点ID，区分根/一级子/二级子 */
    private Long id;
    /** 节点名称 */
    private String name;
    /** 编码 */
    private String code;

    /** 子节点集合 */
    private List<TreeNode> children = new ArrayList<>();

    private Map startDatas;

}
