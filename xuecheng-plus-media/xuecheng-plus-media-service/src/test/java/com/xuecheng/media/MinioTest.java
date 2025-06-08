package com.xuecheng.media;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import io.minio.*;
import io.minio.errors.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.*;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 测试minio的sdk
 */
public class MinioTest {

    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://192.168.101.65:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();

    @Test
    // 上传文件
    public void test_upload() throws Exception {

        // 通过拓展名得到媒体资源类型 mimeType
        ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(".mp4");
        String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE; //通用mimeType，字节流
        if (extensionMatch != null) {
            mimeType = extensionMatch.getMimeType();
        }

        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket("testbucket") // 存储桶名称
//                        .object("ultra_1.jpg") // 存储桶对象名称
                        .object("test/01/287cdd91c5d444e0752b626cbd95b41c.mp4") // 对象名，放在子目录下
                        .filename("D:\\94991\\下载\\287cdd91c5d444e0752b626cbd95b41c.mp4") // 本地文件路径
//                        .contentType("image/jpeg") // 文件类型
                        .contentType(mimeType)
                        .build());
    }

    @Test
    // 删除文件
    public void test_delete() throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket("testbucket") // 存储桶名称
                        .object("test/01/287cdd91c5d444e0752b626cbd95b41c.mp4") // 存储桶对象名称
                        .build());
    }

    @Test
    // 查询文件
    public void test_query() throws Exception {
        // 查询远程服务获取到一个流对象
        FilterInputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket("testbucket") // 存储桶名称
                        .object("test/01/287cdd91c5d444e0752b626cbd95b41c.mp4") // 存储桶对象名称
                        .build());

        // 指定输出流
        FileOutputStream outputStream = new FileOutputStream(new File("D:\\94991\\下载\\1a.mp4"));
        IOUtils.copy(inputStream, outputStream);

        // 校验文件的完整性对文件的内容进行md5
        String source_md5 = DigestUtils.md5Hex(inputStream); //minio中文件的md5
        String local_md5 = DigestUtils.md5Hex(Files.newInputStream(new File("D:\\94991\\下载\\1a.mp4").toPath()));

        if (source_md5.equals(local_md5)) {
            System.out.println("文件上传成功");
        } else {
            System.out.println("文件上传失败");
        }
    }
}
