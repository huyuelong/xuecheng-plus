package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.model.dto.*;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseCategory;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.service.CourseBaseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {

    @Autowired
    private CourseBaseMapper courseBaseMapper;

    @Autowired
    private CourseMarketMapper courseMarketMapper;

    @Autowired
    private CourseCategoryMapper courseCategoryMapper;

    @Override
    public PageResult<CourseBase> queryCourseBaseList(PageParams pageParams, QueryCourseParamsDto courseParamsDto) {

        // 拼装查询条件
        LambdaQueryWrapper<CourseBase> queryWrapper = new LambdaQueryWrapper<>();
        // 根据名称模糊查询，在sql中拼接 course_base.name like '%值%'
        queryWrapper.like(StringUtils.isNotEmpty(courseParamsDto.getCourseName()),CourseBase::getName,courseParamsDto.getCourseName());
        // 根据课程审核状态查询，在sql中拼接 course_base.audit_status = ?
        queryWrapper.eq(StringUtils.isNotEmpty(courseParamsDto.getAuditStatus()),CourseBase::getAuditStatus,courseParamsDto.getAuditStatus());
        //  根据课程发布状态查询，在sql中拼接 course_base.status = ?
        queryWrapper.eq(StringUtils.isNotEmpty(courseParamsDto.getPublishStatus()),CourseBase::getStatus,courseParamsDto.getPublishStatus());

        // 创建page分页参数对象，参数：当前页码，每页记录数
        Page<CourseBase> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
        // 开始进行分页查询
        Page<CourseBase> pageResult = courseBaseMapper.selectPage(page, queryWrapper);
        // 数据列表
        List<CourseBase> items = pageResult.getRecords();
        // 总记录数
        long total = pageResult.getTotal();
        // List<T> items, long counts, long page, long pageSize
        PageResult<CourseBase> courseBasePageResult = new PageResult<>(items, total, pageParams.getPageNo(), pageParams.getPageSize());
        return courseBasePageResult;
    }

    @Transactional
    @Override
    public CourseBaseInfoDto createCourseBase(Long companyId, AddCourseDto dto) {

        // 参数的合法性校验
//        if (StringUtils.isBlank(dto.getName())) {
////            throw new RuntimeException("课程名称为空");
//            XueChengPlusException.cast("课程名称为空");
//        }
//
//        if (StringUtils.isBlank(dto.getMt())) {
//            throw new RuntimeException("课程分类为空");
//        }
//
//        if (StringUtils.isBlank(dto.getSt())) {
//            throw new RuntimeException("课程分类为空");
//        }
//
//        if (StringUtils.isBlank(dto.getGrade())) {
//            throw new RuntimeException("课程等级为空");
//        }
//
//        if (StringUtils.isBlank(dto.getTeachmode())) {
//            throw new RuntimeException("教育模式为空");
//        }
//
//        if (StringUtils.isBlank(dto.getUsers())) {
//            throw new RuntimeException("适应人群为空");
//        }
//
//        if (StringUtils.isBlank(dto.getCharge())) {
//            throw new RuntimeException("收费规则为空");
//        }

        // 向课程基本信息表course_base写入数据
        CourseBase courseBaseNew = new CourseBase();
        // 将传入的页面的参数放到courseBaseNew对象中
        BeanUtils.copyProperties(dto, courseBaseNew); // 只要属性名称一致就可以拷贝
        courseBaseNew.setCompanyId(companyId);
        courseBaseNew.setCreateDate(LocalDateTime.now());
        // 审核状态默认为未提交
        courseBaseNew.setAuditStatus("202002");
        //  发布状态默认为未发布
        courseBaseNew.setStatus("203001");
        // 插入数据库
        int insert = courseBaseMapper.insert(courseBaseNew);
        if (insert <= 0) {
            throw new RuntimeException("添加课程失败");
        }

        // 向课程营销表course_market写入数据
        CourseMarket courseMarketNew = new CourseMarket();
        // 将传入的页面的参数放到courseMarketNew对象中
        BeanUtils.copyProperties(dto, courseMarketNew);
        // 课程id
        Long courseId = courseBaseNew.getId();
        courseMarketNew.setId(courseId);
        // 保存营销信息
        saveCourseMarket(courseMarketNew);
        // 从数据库查询课程的详细信息，包括两部分
        CourseBaseInfoDto courseBaseInfo = getCourseBaseInfo(courseId);

        return courseBaseInfo;
    }

    // 查询课程信息
    public CourseBaseInfoDto getCourseBaseInfo(Long courseId) {
        // 从课程基本信息表查询
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if (courseBase == null) {
            return null;
        }
        // 从课程营销表查询
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);

        // 组装在一起
        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(courseBase, courseBaseInfoDto);
        if (courseMarket != null) {
            BeanUtils.copyProperties(courseMarket, courseBaseInfoDto);
        }
        // 通过courseCategoryMapper查询分类信息，将分类名称设置到courseBaseInfoDto
        // 课程分类的名称设置到 courseBaseInfoDto
        CourseCategory mtObj = courseCategoryMapper.selectById(courseBase.getMt());
        String mtName = mtObj.getName(); // 大分类名称
        courseBaseInfoDto.setMtName(mtName);
        CourseCategory stObj = courseCategoryMapper.selectById(courseBase.getSt());
        String stName = stObj.getName(); // 小分类名称
        courseBaseInfoDto.setStName(stName);

        return courseBaseInfoDto;
    }

    @Override
    public CourseBaseInfoDto updateCourseBase(Long companyId, EditCourseDto editCourseDto) {

        // 拿到课程id
        Long courseId = editCourseDto.getId();
        // 查询课程信息
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        if (courseBase == null) {
            XueChengPlusException.cast("课程不存在");
        }

        // 数据合法性校验
        // 根据具体的业务逻辑去校验
        // 本机构只能修改本机构的课程
        if (!companyId.equals(courseBase.getCompanyId())) {
            XueChengPlusException.cast("本机构只能修改本机构的课程");
        }

        // 封装数据
        BeanUtils.copyProperties(editCourseDto, courseBase);
        // 修改时间
        courseBase.setChangeDate(LocalDateTime.now());

        // 更新基本信息
        int update = courseBaseMapper.updateById(courseBase);
        if (update <= 0) {
            XueChengPlusException.cast("修改课程失败");
        }

        // 更新营销信息
        CourseMarket courseMarket = new CourseMarket();
        BeanUtils.copyProperties(editCourseDto, courseMarket);
        int i = saveCourseMarket(courseMarket);
        if (i <= 0) {
            XueChengPlusException.cast("修改课程营销信息失败");
        }

        // 查询课程信息
        CourseBaseInfoDto courseBaseInfo = getCourseBaseInfo(courseId);

        return courseBaseInfo;
    }

    // 单独写一个方法保存营销信息，逻辑，存在则更新，不存在则添加
    private int saveCourseMarket(CourseMarket courseMarketNew) {
        // 参数的合法性校验
        String charge = courseMarketNew.getCharge();
        if(StringUtils.isBlank(charge)){
            throw new RuntimeException("收费规则没有选择");
        }
        // 如果收费，价格必须输入
        if(charge.equals("201001")){
            if(courseMarketNew.getPrice() == null || courseMarketNew.getPrice() <= 0){
//                throw new RuntimeException("课程为收费价格不能为空且必须大于0");
                XueChengPlusException.cast("课程为收费价格不能为空且必须大于0");
            }
        }

        // 从数据库查询营销信息，存在则更新，不存在则添加
        Long id = courseMarketNew.getId();
        CourseMarket courseMarket = courseMarketMapper.selectById(id);
        if (courseMarket == null) {
            // 插入
            return courseMarketMapper.insert(courseMarketNew);
        } else {
            // 将courseMarketNew拷贝到courseMarket
            BeanUtils.copyProperties(courseMarketNew, courseMarket);
            courseMarketNew.setId(courseMarketNew.getId());
            // 更新
            return courseMarketMapper.updateById(courseMarket);
        }

    }
}
