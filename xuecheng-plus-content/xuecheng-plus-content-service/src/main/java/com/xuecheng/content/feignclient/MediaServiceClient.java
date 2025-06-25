package com.xuecheng.content.feignclient;

import com.xuecheng.content.config.MultipartSupportConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/*
 * 远程调用媒资服务的接口
 */
// 使用fallback定义降级类是无法获取熔断异常信息的，使用fallbackFactory定义降级类可以获取熔断异常信息
@FeignClient(value = "media-api", configuration = { MultipartSupportConfig.class }, fallbackFactory = MediaServiceClientFallbackFactory.class)
public interface MediaServiceClient {

    @RequestMapping(value = "/upload/coursefile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@RequestPart("filedata") MultipartFile filedata,
                                 @RequestParam(value = "objectName", required = false) String objectName);
}
