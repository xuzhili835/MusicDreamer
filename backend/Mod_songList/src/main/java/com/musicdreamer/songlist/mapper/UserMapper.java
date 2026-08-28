package com.musicdreamer.songlist.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 用户表只读 Mapper：歌单/专辑列表补创建者（作者）昵称。 */
@Mapper
public interface UserMapper {

    @Select("<script>SELECT id, COALESCE(NULLIF(nickname, ''), username) AS name FROM user WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectNamesByIds(@Param("ids") List<Long> ids);
}
