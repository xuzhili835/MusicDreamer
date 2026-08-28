package com.musicdreamer.media.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** landmark 指纹倒排索引存取（批量写入 + 查询按 hash IN 拉命中行）。 */
@Mapper
public interface FingerprintMapper {

    @Delete("DELETE FROM fp_hash WHERE song_id = #{songId}")
    int deleteBySong(@Param("songId") Long songId);

    /** rows: 每行 Map(h=hash, s=songId, p=pos)，调用方按 500 行分批。
     *  不用 List<long[]> + #{r[0]}：MyBatis foreach 对原始类型数组下标取值会 BindingException。 */
    @Insert("<script>INSERT INTO fp_hash (hash, song_id, pos) VALUES " +
            "<foreach collection='rows' item='r' separator=','>(#{r.h}, #{r.s}, #{r.p})</foreach>" +
            "</script>")
    int insertBatch(@Param("rows") List<Map<String, Long>> rows);

    /** 查询哈希命中的 (hash, songId, 库内锚点帧)，调用方按 500 个哈希分批。 */
    @Select("<script>SELECT hash, song_id AS songId, pos FROM fp_hash WHERE hash IN (" +
            "<foreach collection='hs' item='h' separator=','>#{h}</foreach>" +
            ")</script>")
    List<Map<String, Object>> lookup(@Param("hs") List<Integer> hs);

    @Select("SELECT COUNT(DISTINCT song_id) FROM fp_hash")
    int songCount();
}
