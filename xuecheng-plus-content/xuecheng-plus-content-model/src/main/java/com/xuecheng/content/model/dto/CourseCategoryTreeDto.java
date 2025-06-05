package com.xuecheng.content.model.dto;

import com.xuecheng.content.model.po.CourseCategory;
import lombok.Data;

import java.util.List;

/**
 * 课程分类树型结点 Dto
 */
@Data
public class CourseCategoryTreeDto extends CourseCategory implements java.io.Serializable{

    //子结点
    List<CourseCategoryTreeDto> childrenTreeNodes;
}
