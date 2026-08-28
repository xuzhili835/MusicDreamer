package com.musicdreamer.music.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 歌曲专辑字段与专辑实体的联动（跨模块表直写）。
 *
 * album / album_song 归 Mod_songList 领域，但两服务共用同一数据库；
 * 编辑/提交歌曲时若填了专辑名，按同名专辑幂等补挂 album_song，
 * 免去"改完专辑字段还得去专辑页手动加歌"的跨服务编排。
 * 只增不删——移出专辑仍走专辑页的"从专辑移除"。
 */
@Mapper
public interface AlbumLinkMapper {

    /** 按名字精确匹配专辑（不区分归属：管理员导入他人作品入他人专辑是合法诉求）。 */
    @Select("SELECT id FROM album WHERE name = #{name}")
    List<Long> matchAlbumIds(@Param("name") String name);

    /** 幂等挂链：已有该(专辑,歌曲)则不动；sort_order 排到该专辑末尾。 */
    @Insert("INSERT INTO album_song (album_id, song_id, sort_order) "
            + "SELECT #{albumId}, #{songId}, "
            + "IFNULL((SELECT s.sort_order FROM album_song s WHERE s.album_id = #{albumId} ORDER BY s.sort_order DESC LIMIT 1), -1) + 1 "
            + "WHERE NOT EXISTS (SELECT 1 FROM album_song WHERE album_id = #{albumId} AND song_id = #{songId})")
    int insertIfAbsent(@Param("albumId") Long albumId, @Param("songId") Long songId);
}
