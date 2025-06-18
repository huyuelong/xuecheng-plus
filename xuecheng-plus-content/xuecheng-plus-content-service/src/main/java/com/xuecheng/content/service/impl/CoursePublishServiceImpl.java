package com.xuecheng.content.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.mapper.CoursePublishPreMapper;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.CoursePublishPre;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.content.service.TeachplanService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程发布相关接口实现
 */
@Slf4j
@Service
public class CoursePublishServiceImpl implements CoursePublishService {

    @Autowired
    private CourseBaseInfoService courseBaseInfoService;
    @Autowired
    private TeachplanService teachplanService;
    @Autowired
    private CourseBaseMapper courseBaseMapper;
    @Autowired
    private CourseMarketMapper courseMarketMapper;
    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;

    @Override
    public CoursePreviewDto getCoursePreviewInfo(Long courseId) {
        CoursePreviewDto coursePreviewDto = new CoursePreviewDto();
        // 课程基本信息，营销信息
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        coursePreviewDto.setCourseBase(courseBaseInfo);
        // 课程计划信息
        List<TeachplanDto> teachplanTree = teachplanService.findTeachplanTree(courseId);
        coursePreviewDto.setTeachplans(teachplanTree);
        return null;
    }

    @Transactional
    @Override
    public void commitAudit(Long companyId, Long courseId) {

         CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
         if (courseBaseInfo == null) {
             XueChengPlusException.cast("课程找不到");
         }
         // 审核状态
        String auditStatus = courseBaseInfo.getAuditStatus();

        // 如果课程的审核为已提交则不允许提交
        if (auditStatus.equals("202003")) {
            XueChengPlusException.cast("课程已提交审核，请等待结果");
        }

        // 本机构只能提交本机构的课程
        if(!courseBaseInfo.getCompanyId().equals(companyId)){
            XueChengPlusException.cast("不允许提交其它机构的课程。");
        }

        // 课程的图片，计划信息没有填写也不允许提交
        String pic = courseBaseInfo.getPic();
        if (StringUtils.isEmpty(pic)) {
            XueChengPlusException.cast("请上传课程图片");
        }
        List<TeachplanDto> teachplanTree = teachplanService.findTeachplanTree(courseId);
        if (teachplanTree == null || teachplanTree.size() == 0) {
            XueChengPlusException.cast("请编写课程计划");
        }

        // 查询到课程的基本信息，营销信息，计划等信息插入到课程预发布表
        CoursePublishPre coursePublishPre = new CoursePublishPre();
        BeanUtils.copyProperties(courseBaseInfo, coursePublishPre);
        // 设置机构id
        coursePublishPre.setCompanyId(companyId);
        // 营销信息
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        // 转json
        String courseMarketJson = JSON.toJSONString(courseMarket);
        coursePublishPre.setMarket(courseMarketJson);
        // 计划信息
        String teachplanTreeString = JSON.toJSONString(teachplanTree);
        coursePublishPre.setTeachplan(teachplanTreeString);
        // 状态
        coursePublishPre.setStatus("202003");
        // 提交时间
        coursePublishPre.setCreateDate(LocalDateTime.now());
        // 查询预发布表，如果有记录则更新，没有则插入
        CoursePublishPre coursePublishPreObj = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPreObj != null) {
            // 更新
            coursePublishPreMapper.updateById(coursePublishPre);
        } else {
            // 插入
            coursePublishPreMapper.insert(coursePublishPre);
        }

        // 更新课程基本信息表的审核状态尾已提交
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        courseBase.setAuditStatus("202003"); // 审核状态为已提交

        courseBaseMapper.updateById(courseBase);

    }
}
