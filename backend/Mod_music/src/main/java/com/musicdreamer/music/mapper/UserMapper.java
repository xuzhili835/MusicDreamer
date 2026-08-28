package com.musicdreamer.music.mapper;

import com.musicdreamer.music.vo.SingerVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 用户表只读 Mapper（join 用，歌手搜索）。 */
public interface UserMapper {

    /** 按 ID 查角色（导入管线判断操作者是否管理员：0 听众 1 歌手 2 管理员）。 */
    @Select("SELECT role FROM user WHERE id = #{id}")
    Integer selectRole(@Param("id") Long id);

    /** 批量查展示名（昵称缺失回退用户名），歌单/专辑详情拼装歌手名用。 */
    @Select("<script>SELECT id, COALESCE(NULLIF(nickname, ''), username) AS name FROM user WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectNamesByIds(@Param("ids") List<Long> ids);

    String SINGER_COLS = "u.id, COALESCE(u.nickname, u.username) AS nickname, u.avatar, sp.fans_count AS fansCount";

    /** 歌手搜索：role >= 1 且用户名/昵称匹配。 */
    @Select("<script>SELECT " + SINGER_COLS + " FROM user u LEFT JOIN singer_profile sp ON sp.user_id = u.id "
            + "WHERE u.role >= 1 AND u.status = 1 "
            + "<if test='kw != null and kw != \"\"'> AND (u.username LIKE CONCAT('%', #{kw}, '%') "
            + "OR u.nickname LIKE CONCAT('%', #{kw}, '%'))</if> "
            + "ORDER BY u.id DESC LIMIT #{offset}, #{size}</script>")
    List<SingerVO> searchSingers(@Param("kw") String kw, @Param("offset") long offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM user u WHERE u.role >= 1 AND u.status = 1 "
            + "<if test='kw != null and kw != \"\"'> AND (u.username LIKE CONCAT('%', #{kw}, '%') "
            + "OR u.nickname LIKE CONCAT('%', #{kw}, '%'))</if></script>")
    long countSingers(@Param("kw") String kw);
}
