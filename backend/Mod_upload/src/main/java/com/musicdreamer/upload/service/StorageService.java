package com.musicdreamer.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 按类型分目录落盘：music / image / lyric / task（设计 7.2 节存储规范）。 */
@Slf4j
@Service
public class StorageService {

    @Value("${upload.storage-root:./data}")
    private String storageRoot;

    public Path dir(String type) {
        Path p = Paths.get(storageRoot, type).toAbsolutePath().normalize();
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储目录 " + p, e);
        }
        return p;
    }

    /** 保存文件到指定类型目录，返回相对 URL（/data/{type}/{name}）。 */
    public String save(String type, String originalName, InputStream in) {
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') >= 0) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ext;
        Path target = dir(type).resolve(name);
        try (InputStream src = in) {
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败：" + target, e);
        }
        return "/data/" + type + "/" + name;
    }
}
