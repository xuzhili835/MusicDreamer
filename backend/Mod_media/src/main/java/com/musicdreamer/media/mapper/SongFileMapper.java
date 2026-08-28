package com.musicdreamer.media.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 只读 song 表的音频定位信息（指纹重建用；Mod_media 与 Mod_music 共库）。 */
@Mapper
public interface SongFileMapper {

    @Select("SELECT id AS songId, file_url AS fileUrl FROM song " +
            "WHERE file_url IS NOT NULL AND file_url != ''")
    List<Map<String, Object>> listSongFiles();

    /** 外置识别歌名回查本地用（已发布歌曲的展示字段）。 */
    @Select("SELECT id AS songId, name, singer_name AS singerName, cover_url AS coverUrl, duration " +
            "FROM song WHERE status = 2 AND name IS NOT NULL AND name != ''")
    List<Map<String, Object>> listPublishedSongs();
}
