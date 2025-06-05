package com.xuecheng.content.service.impl;

import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.model.dto.CourseCategoryTreeDto;
import com.xuecheng.content.service.CourseCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    @Autowired
    private CourseCategoryMapper courseCategoryMapper;

    @Override
    public List<CourseCategoryTreeDto> queryTreeNodes(String id) {
        // 调用mapper递归查询出分类信息
        List<CourseCategoryTreeDto> courseCategoryTreeDtos = courseCategoryMapper.selectTreeNodes(id);

        // 找到每个节点的子节点，最终封装成List<CourseCategoryTreeDto>
        // 先将list转成map，key就是节点的id，value是CourseCategoryTreeDto对象，目的就是为了方便从map获取节点
        Map<String, CourseCategoryTreeDto> mapTemp = courseCategoryTreeDtos.stream().filter(item -> !id.equals(item.getId())).collect(Collectors.toMap(key -> key.getId(), value -> value, (key1, key2) -> key2));

        // 定义一个list，用来存储最终的树形结构
        List<CourseCategoryTreeDto> courseCategoryList = new ArrayList<>();
        // 从头遍历List<CourseCategoryTreeDto>，一边遍历一边找子节点放在父节点的childrenTreeNodes属性中
        courseCategoryTreeDtos.stream().filter(item -> !id.equals(item.getId())).forEach(item -> {
            if  (item.getParentid().equals(id)) {
                courseCategoryList.add(item);
            }
            // 找到节点的父节点
            CourseCategoryTreeDto courseCategoryParent = mapTemp.get(item.getParentid());
            if (courseCategoryParent != null) {
                if (courseCategoryParent.getChildrenTreeNodes() == null) {
                    // 如果父节点的childrenTreeNodes属性为空，则创建一个，因为要向该集合中放它的子节点
                    courseCategoryParent.setChildrenTreeNodes(new ArrayList<>());
                }
                // 找到每个节点的子节点放在父节点的childrenTreeNodes属性中
                courseCategoryParent.getChildrenTreeNodes().add(item);
            }
        });

        return courseCategoryList;
    }
}
