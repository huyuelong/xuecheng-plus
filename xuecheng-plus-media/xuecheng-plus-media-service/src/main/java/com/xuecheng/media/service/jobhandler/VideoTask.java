package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 任务处理类
 */
@Slf4j
@Component
public class VideoTask {

    @Autowired
    private MediaFileProcessService mediaFileProcessService;
    @Autowired
    private MediaFileService mediaFileService;

    // ffmpeg的路径
    @Value("${videoprocess.ffmpegpath}")
    private String ffmpegPath;

    /**
     * 视频处理任务
     */
    @XxlJob("videoJobHandler")
    public void videoJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex(); //执行器的序号，从0开始
        int shardTotal = XxlJobHelper.getShardTotal(); //执行器的总数

        // 确定cpu的核心数
        int processors = Runtime.getRuntime().availableProcessors();

        // 查询待处理的任务
        List<MediaProcess> mediaProcessList = mediaFileProcessService.getMediaProcessList(shardIndex, shardTotal, processors);
        // 任务数量
        int size = mediaProcessList.size();
        log.debug("取到的视频处理任务数：" + size);
        if (size <= 0) {
            return;
        }

        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(size);
        // 使用的计数器
        CountDownLatch countDownLatch = new CountDownLatch(size);
        mediaProcessList.forEach(mediaProcess -> {
            // 将任务加入线程池
            executorService.execute(() -> {
                try {
                    // 任务id
                    Long taskId = mediaProcess.getId();
                    // 文件id就是md5
                    String fileId = mediaProcess.getFileId();
                    // 开启任务
                    boolean b = mediaFileProcessService.startTask(taskId);
                    if (!b) {
                        log.debug("抢占任务失败，任务id: {}", taskId);
                        return;
                    }

                    // 执行视频转码

                    String bucket = mediaProcess.getBucket(); // 桶
                    // objectName
                    String objectName = mediaProcess.getFilePath(); // objectName
                    // 下载minio视频到本地
                    File file = mediaFileService.downloadFileFromMinIO(bucket, objectName);
                    if (file == null) {
                        log.debug("下载视频出错，任务id: {}，bucket: {}, object: {}", taskId, bucket, objectName);
                        // 保存任务失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "3", mediaProcess.getFileId(), null, "下载视频到本地失败");
                        return;
                    }

                    // ffmpeg的路径
                    // 源avi视频的路径
                    String video_path = file.getAbsolutePath();
                    // 转换后mp4文件的名称
                    String mp4_name = fileId + ".mp4";
                    // 转换后mp4文件的路径
                    // 先创建一个临时文件，作为转换后的文件
                    File mp4file = null;
                    try {
                        mp4file = File.createTempFile("minio", ".mp4");
                    } catch (IOException e) {
                        log.debug("创建临时文件异常，{}", e.getMessage());
                        // 保存任务失败的结果
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "3", mediaProcess.getFileId(), null, "创建临时文件异常");
                        return;
                    }
                    String mp4_path = mp4file.getAbsolutePath();
                    //创建工具类对象
                    Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpegPath, video_path, mp4_name, mp4_path);
                    //开始视频转换，成功将返回success，失败返回失败原因
                    String result = videoUtil.generateMp4();
                    if (!"success".equals(result)) {
                        log.debug("视频转码失败，原因: {}，bucket: {}, objectname: {}", result, bucket, objectName);
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "3", mediaProcess.getFileId(), null, result);
                        return;
                    }
                    // 上传到minio
                    boolean b1 = mediaFileService.addMediaFilesToMinIO(mp4file.getAbsolutePath(), "video/mp4", bucket, objectName);
                    if (!b1) {
                        log.debug("上传mp4到minio失败，任务id: {}", taskId);
                        mediaFileProcessService.saveProcessFinishStatus(taskId, "3", mediaProcess.getFileId(), null, "上传mp4到minio失败");
                        return;
                    }
                    // mp4文件的url
                    String url = getFilePath(fileId, ".mp4");

                    // 更新任务状态为成功
                    mediaFileProcessService.saveProcessFinishStatus(taskId, "2", fileId, url, null);
                } finally {
                    // 计数器减1
                    countDownLatch.countDown();
                }

            });
        });

        // 阻塞，指定最大限制的等待时间，阻塞最多等待一定的时间后就解除阻塞
        countDownLatch.await(30, TimeUnit.MINUTES);

    }

    private String getFilePath(String fileMd5, String fileExt) {
        return fileMd5.charAt(0) + "/" + fileMd5.charAt(1) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }

}
