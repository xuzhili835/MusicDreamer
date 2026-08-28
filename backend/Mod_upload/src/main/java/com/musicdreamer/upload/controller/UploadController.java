package com.musicdreamer.upload.controller;

import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.api.Mess;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.upload.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传（设计第 7 章）：上传主体为歌手与管理员（role>=1）；
 * 校验链：扩展名白名单 -> 文件头 Magic -> 大小限制；产出的歌曲进入审核流程。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storage;

    private static final Set<String> AUDIO_EXT = Set.of("mp3", "flac", "aac");
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png");
    private static final Set<String> LYRIC_EXT = Set.of("lrc");

    /** 音频文件头 Magic：ID3（mp3）/ fLaC（flac）；AAC 无固定头，放行扩展名校验。 */
    private static final byte[] MP3_MAGIC = {'I', 'D', '3'};
    private static final byte[] FLAC_MAGIC = {'f', 'L', 'a', 'C'};

    @PostMapping("/audio")
    public Mess audio(@RequestParam("file") MultipartFile file) throws IOException {
        AuthContext.requireUploader();
        check(file, AUDIO_EXT, 50L * 1024 * 1024);
        checkAudioMagic(file);
        String url = storage.save("music", file.getOriginalFilename(), file.getInputStream());
        return Mess.ok(Map.of("url", url, "size", file.getSize()));
    }

    @PostMapping("/image")
    public Mess image(@RequestParam("file") MultipartFile file) throws IOException {
        AuthContext.requireUploader();
        check(file, IMAGE_EXT, 5L * 1024 * 1024);
        String url = storage.save("image", file.getOriginalFilename(), file.getInputStream());
        return Mess.ok(Map.of("url", url));
    }

    @PostMapping("/lyric")
    public Mess lyric(@RequestParam("file") MultipartFile file) throws IOException {
        AuthContext.requireUploader();
        check(file, LYRIC_EXT, 1024 * 1024);
        // 繁→简统一（bug19）：与在线词库/转写产出的歌词同一规范
        String content = new String(file.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String converted;
        try {
            converted = com.github.houbb.opencc4j.util.ZhConverterUtil.toSimple(content);
        } catch (Exception e) {
            converted = content;
        }
        String url = storage.save("lyric", file.getOriginalFilename(),
                new java.io.ByteArrayInputStream(converted.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return Mess.ok(Map.of("url", url));
    }

    private void check(MultipartFile file, Set<String> exts, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_MISSING);
        }
        String name = file.getOriginalFilename();
        String ext = ext(name);
        if (!exts.contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        }
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    /** 文件头 Magic 校验，拦截伪造扩展名（如可执行文件改名 mp3）。 */
    private void checkAudioMagic(MultipartFile file) throws IOException {
        byte[] head = new byte[4];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, 4);
        }
        boolean mp3 = n >= 3 && starts(head, MP3_MAGIC);
        boolean flac = n >= 4 && starts(head, FLAC_MAGIC);
        // AAC（ADTS）头为 FF Fx；也接受 MP3 帧头 FF Fx
        boolean aacOrMpegFrame = n >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xF0) != 0xF0;
        if (!(mp3 || flac || aacOrMpegFrame)) {
            throw new BizException(ErrorCode.SONG_FORMAT_ERROR);
        }
    }

    private boolean starts(byte[] data, byte[] magic) {
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    private String ext(String name) {
        if (name == null || name.lastIndexOf('.') < 0) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
