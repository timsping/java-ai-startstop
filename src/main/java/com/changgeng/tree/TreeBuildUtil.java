package com.changgeng.tree;

import com.changgeng.pojo.SourceRecord;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 根据 SourceRecord 的 eventId / childEventId / childEventId1 组装三层树形
 * 根节点:eventId
 * 一级子:childEventId
 * 二级子:childEventId1
 * JDK8版本
 */
public class TreeBuildUtil {

    /**
     * 入口方法：把原始记录集合转为根节点树列表
     * @param sourceList json解析后的原始数据
     * @return 根节点树
     */
    public static List<TreeNode> buildTree(List<SourceRecord> sourceList) {
        // key:节点ID value:节点对象，用于快速查找复用，避免重复创建相同id节点
        Map<Long, TreeNode> nodeMap = new HashMap<>();

        for (SourceRecord record : sourceList) {
            // ========== 1.构建根节点 eventId ==========
            TreeNode root = nodeMap.computeIfAbsent(record.getEventId(), id -> {
                TreeNode treeNode = new TreeNode();
                treeNode.setId(id);
                treeNode.setName(record.getEventName());
                treeNode.setCode(record.getEventCode());
                return treeNode;
            });

            // ========== 2.构建一级子节点 childEventId ==========
            TreeNode level1 = nodeMap.computeIfAbsent(record.getChildEventId(), id -> {
                TreeNode treeNode = new TreeNode();
                treeNode.setId(id);
                treeNode.setName(record.getChildEventName());
                treeNode.setCode(record.getChildEventCode());
                return treeNode;
            });

            // 根节点的children中不存在该一级子节点，才加入，防止重复添加
            if (!isChildExist(root.getChildren(), level1.getId())) {
                root.getChildren().add(level1);
            }

            // ==========3.构建二级子节点 childEventId1 ==========
            TreeNode level2 = nodeMap.computeIfAbsent(record.getChildEventId1(), id -> {
                TreeNode treeNode = new TreeNode();
                treeNode.setId(id);
                treeNode.setName(record.getChildEventName1());
                treeNode.setCode(record.getChildEventCode1());
                return treeNode;
            });

            // 一级子节点children不存在该二级节点，才加入
            if (!isChildExist(level1.getChildren(), level2.getId())) {
                level1.getChildren().add(level2);
            }
        }

        // 获取所有根节点ID集合
        Set<Long> rootIds = sourceList.stream()
                .map(SourceRecord::getEventId)
                .collect(Collectors.toSet());

        return rootIds.stream()
                .map(nodeMap::get)
                .collect(Collectors.toList());
    }

    /**
     * 判断子节点列表是否已经包含该id节点，去重
     */
    private static boolean isChildExist(List<TreeNode> children, Long targetId) {
        if (children == null || children.isEmpty()) {
            return false;
        }
        for (TreeNode n : children) {
            if (Objects.equals(n.getId(), targetId)) {
                return true;
            }
        }
        return false;
    }


    // =====================调试打印树【JDK8兼容，上线可以删除】=====================
    public static void printTree(List<TreeNode> treeList) {
        for (TreeNode treeNode : treeList) {
            printNode(treeNode, 0);
        }
    }

    private static void printNode(TreeNode node, int level) {
        StringBuilder prefixSb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            prefixSb.append("  ");
        }
        String prefix = prefixSb.toString();
        System.out.printf("%s[id:%d,name:%s,code:%s]%n", prefix, node.getId(), node.getName(), node.getCode());
        for (TreeNode child : node.getChildren()) {
            printNode(child, level + 1);
        }
    }
}
