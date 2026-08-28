package com.musicdreamer.media.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 跨服务表的只读查询（全服务共用同一个 music 库，按需读最小字段，不引入反向 Feign）。
 * 只做 SELECT，写操作仍归各自服务，避免双写。
 */
@Mapper
public interface CrossReadMapper {

    /** bug80：按昵称找在用歌手/管理员账号 id——求歌入库把歌归属到同名歌手。 */
    @Select("SELECT id FROM `user` WHERE nickname = #{nickname} AND role IN (1, 2) AND status = 1 "
            + "ORDER BY id LIMIT 1")
    Long findSingerIdByNickname(@Param("nickname") String nickname);

    /** bug85：按歌名找已发布歌曲——求歌本地已有同名曲直接完成，不再走下载。 */
    @Select("SELECT id, name FROM song WHERE name = #{name} AND status = 2 ORDER BY id LIMIT 1")
    Map<String, Object> findPublishedSongByName(@Param("name") String name);
}
