package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;

import java.util.List;

/**
 * 课程计划管理相关接口
 */
public interface TeachplanService {
    /**
     * 根据课程id查询课程计划，组成树形结构
     * @param courseId 课程id
     * @return
     */
    public List<TeachplanDto> findTeachplanTree(long courseId);

    /**
     * 新增/修改/保存课程计划
     * @param saveTeachplanDto
     */
    public void saveTeachplan(SaveTeachplanDto saveTeachplanDto);

    /**
     * 课程计划和媒资文件绑定
     * @param bindTeachplanMediaDto
     */
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);
}
